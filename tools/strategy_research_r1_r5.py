#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""R1-R5 research pipeline; research-only, never writes production strategy."""
from __future__ import annotations
import argparse, json, math, random
from pathlib import Path
import numpy as np
import pandas as pd
import tradingview_strategy_lab as lab


def diag(trades):
    if not trades: return {"trades":0,"median_r":0.0,"std_r":0.0,"max_consecutive_loss":0,"mfe_proxy":0.0}
    rs=np.array([t.pnl_r for t in trades],float)
    run=mx=0
    for x in rs:
        if x<0: run+=1; mx=max(mx,run)
        else: run=0
    return {"trades":len(rs),"median_r":float(np.median(rs)),"std_r":float(rs.std(ddof=1)) if len(rs)>1 else 0.0,"max_consecutive_loss":mx}


def stress(df, kind, params, base_cfg, split, sl, tp):
    out=[]
    for label, mult, slip in [("base",1.0,0.0),("spread_1p5",1.5,0.0),("adverse",1.5,0.05)]:
        cfg=lab.Config(initial_balance=base_cfg.initial_balance,risk_pct=base_cfg.risk_pct,spread=base_cfg.spread*mult,commission_pct=base_cfg.commission_pct,slippage=slip,min_trades=base_cfg.min_trades)
        sig=lab.strategy_signal(df,kind,params)
        m=lab.metrics(lab.simulate(df,sig,cfg,*split,sl,tp),cfg.initial_balance)
        out.append({"scenario":label,**m})
    return out


def wfo(df, kind, params, cfg, sl,tp, windows=4):
    n=len(df); train=max(800,int(n*0.35)); test=max(400,int(n*0.15)); rows=[]
    for k in range(windows):
        a=k*test; tr_end=a+train; b=tr_end+test
        if b>n: break
        sig=lab.strategy_signal(df,kind,params)
        m=lab.metrics(lab.simulate(df,sig,cfg,tr_end,b,sl,tp),cfg.initial_balance)
        rows.append({"window":k+1,"train_end":tr_end,"test_end":b,**m})
    return rows


def monte_carlo(rs, reps=3000, seed=42):
    rng=np.random.default_rng(seed); rs=np.asarray(rs,float)
    if len(rs)<2: return {"reps":0,"p05_final_r":0.0,"p50_final_r":0.0,"p95_final_r":0.0,"p05_max_dd_r":0.0}
    finals=[]; dds=[]
    for _ in range(reps):
        x=rng.choice(rs,size=len(rs),replace=True); eq=np.cumsum(x); peak=np.maximum.accumulate(np.r_[0,eq]); dd=peak-np.r_[0,eq]
        finals.append(eq[-1]); dds.append(dd.max())
    return {"reps":reps,"p05_final_r":float(np.percentile(finals,5)),"p50_final_r":float(np.percentile(finals,50)),"p95_final_r":float(np.percentile(finals,95)),"p95_max_dd_r":float(np.percentile(dds,95))}


def permutation(rs, reps=3000, seed=43):
    rs=np.asarray(rs,float)
    if len(rs)<2:return {"reps":0,"observed_sum_r":float(rs.sum()),"p_value":1.0}
    rng=np.random.default_rng(seed); obs=abs(rs.sum()); ge=0
    for _ in range(reps):
        x=rs*rng.choice(np.array([-1.0,1.0]),size=len(rs)); ge += abs(x.sum())>=obs
    return {"reps":reps,"observed_sum_r":float(rs.sum()),"p_value":float((ge+1)/(reps+1))}


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--symbol',default='OANDA:XAUUSD'); ap.add_argument('--interval',default='15m'); ap.add_argument('--bars',type=int,default=5753); ap.add_argument('--csv',default=None); ap.add_argument('--strategies',default=None,help='Comma list; parts are saved per strategy and merged, so runs are resumable in chunks.'); ap.add_argument('--shard',default=None,help='e.g. 0/2 runs candidates with index %% 2 == 0; part file is suffixed so shards merge.'); ap.add_argument('--out',default='strategy_lab_output/xauusd_15m_r1_r5'); args=ap.parse_args()
    out=Path(args.out); out.mkdir(parents=True,exist_ok=True); parts_dir=out/'parts'; parts_dir.mkdir(exist_ok=True)
    df=lab.load_csv(args.csv) if args.csv else lab.load_tv(args.symbol,args.interval,args.bars)
    cfg=lab.Config(min_trades=30); sl=1.5; tp=2.0
    n=len(df); split={"train":(0,int(n*.60)),"validation":(int(n*.60),int(n*.80)),"holdout":(int(n*.80),n)}
    kinds=[k.strip().upper() for k in args.strategies.split(',') if k.strip()] if args.strategies else list(lab.GRIDS)
    shard=None
    if args.shard: s,k=args.shard.split('/'); shard=(int(s),int(k))
    for kind in kinds:
        grid=lab.GRIDS[kind]; rows=[]; cands=list(lab.param_product(grid))
        if shard: cands=[c for i,c in enumerate(cands) if i%shard[1]==shard[0]]
        for p in cands:
            parts=lab.evaluate_candidate(df,kind,p,cfg,split,sl,tp); tr,va,ho=parts['train'],parts['validation'],parts['holdout']
            st,_=lab.neighborhood_stability(df,kind,p,cfg,split,sl,tp)
            if tr['trades']<cfg.min_trades: continue
            # Selection uses TRAIN + VALIDATION only; holdout is reported but never used in ranking.
            selection=(0.60*lab.score(tr)+0.40*lab.score(va)) + 8.0*st
            rows.append({"strategy":kind,"params":json.dumps(p,sort_keys=True),"selection_score":selection,"neighborhood_stability":st,"train_trades":tr['trades'],"validation_trades":va['trades'],"validation_expectancy_r":va['expectancy_r'],"holdout_trades":ho['trades'],"holdout_expectancy_r":ho['expectancy_r'],"holdout_pf":ho['pf'],"holdout_max_dd_pct":ho['max_dd_pct']})
        tag=f'{kind}__{shard[0]}of{shard[1]}' if shard else kind
        pd.DataFrame(rows).to_csv(parts_dir/f'{tag}.csv',index=False,encoding='utf-8-sig')
        print(f'[{tag}] {len(rows)} candidates saved -> {parts_dir/(tag+".csv")}',flush=True)
    frames=[pd.read_csv(f) for f in sorted(parts_dir.glob('*.csv'))]
    cand=pd.concat(frames,ignore_index=True).sort_values('selection_score',ascending=False)
    cand.to_csv(out/'r1_candidate_matrix.csv',index=False,encoding='utf-8-sig')
    # Require validation non-negative and stability >= .5; inspect top 10, choose highest selection score.
    eligible=cand[(cand.validation_expectancy_r>=0)&(cand.neighborhood_stability>=0.5)&(cand.validation_trades>=10)]
    chosen=eligible.iloc[0].to_dict() if not eligible.empty else None
    report={"lab":"TV-Strategy-Lab-1.0","research":"R1-R5","data":{"symbol":args.symbol,"interval":args.interval,"bars":n,"first":str(df.timestamp.iloc[0]),"last":str(df.timestamp.iloc[-1])},"split":split,"candidate_count":len(cand),"eligible_count":len(eligible),"chosen":chosen}
    if chosen:
        kind=chosen['strategy']; params=json.loads(chosen['params']); sig=lab.strategy_signal(df,kind,params); trades=lab.simulate(df,sig,cfg,*split['holdout'],sl,tp); report['holdout_diag']=diag(trades); report['cost_stress']=stress(df,kind,params,cfg,split['holdout'],sl,tp); report['walk_forward']=wfo(df,kind,params,cfg,sl,tp); rs=[t.pnl_r for t in trades]; report['monte_carlo']=monte_carlo(rs); report['permutation']=permutation(rs)
        # final gate: enough holdout trades + positive holdout + all WFO windows positive + adverse stress positive
        wf=report['walk_forward']; adv=[x for x in report['cost_stress'] if x['scenario']=='adverse'][0]
        report['final_gate']={"holdout_sample_ok":len(trades)>=30,"holdout_positive":bool(report['holdout_diag']['median_r']>0 and report['chosen']['holdout_expectancy_r']>0),"wfo_consistency":bool(wf and sum(x['expectancy_r']>0 for x in wf)/len(wf)>=0.75),"adverse_cost_positive":bool(adv['expectancy_r']>0),"permutation_p_lt_0_05":bool(report['permutation']['p_value']<0.05)}
        report['promotion_ready']=all(report['final_gate'].values())
    else: report['promotion_ready']=False; report['final_gate_reason']='No candidate passed validation/stability gates.'
    (out/'r1_r5_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
