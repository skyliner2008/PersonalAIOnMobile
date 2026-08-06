console.log('================================================================');
console.log('                  📊 TRADING ANALYTICS TOOLS HELP                 ');
console.log('================================================================');
console.log('\nระบบมีเครื่องมือวิเคราะห์ (Analytics) หลายรูปแบบให้เลือกใช้งาน ดังนี้:\n');

console.log('1️⃣  npm run analyze');
console.log('    • คำอธิบาย: วิเคราะห์ภาพรวมพอร์ตโฟลิโอแบบ All-Time หรือระบุวันที่');
console.log('    • รูปแบบการใช้:');
console.log('      - npm run analyze                 -> วิเคราะห์ทั้งหมด (All-Time)');
console.log('      - npm run analyze 2026-05-15      -> วิเคราะห์เฉพาะวันที่ระบุ');
console.log('    • ข้อมูลที่ได้: PnL, Win Rate, Expected Payoff, Profit Factor, Max Drawdown, Win/Loss Streak, สถิติการบล็อคราย Gate\n');

console.log('2️⃣  npm run analytics:gate');
console.log('    • คำอธิบาย: วิเคราะห์และเจาะลึกรายละเอียดของออเดอร์ที่ถูก Gate บล็อค');
console.log('    • รูปแบบการใช้:');
console.log('      - npm run analytics:gate              -> แสดงสรุปสถิติทุก Gate (7 วันย้อนหลัง)');
console.log('      - npm run analytics:gate <CATEGORY>   -> แสดงรายละเอียด Data Snapshot 20 ออเดอร์ล่าสุดของ Gate นั้น');
console.log('    • ตัวอย่าง: npm run analytics:gate ZONE_GATE');
console.log('    • ข้อมูลที่ได้: ข้อมูลเชิงลึกราย Timeframe, เหตุผลที่บล็อคแบบละเอียด, Slippage, RRR Degradation\n');

console.log('3️⃣  npm run analyze:logs (Python)');
console.log('    • คำอธิบาย: วิเคราะห์ Log file ของระบบด้วย Python Script (เน้นดูการทำงานของ AI และระบบเบื้องหลัง)');
console.log('    • รูปแบบการใช้: npm run analyze:logs');
console.log('    • ข้อมูลที่ได้: การเชื่อมต่อ, Error Rates, AI Response Times\n');

console.log('4️⃣  npm run analyze:deep (Python)');
console.log('    • คำอธิบาย: ทำ Deep Analysis ของ Trade Data ผ่าน Python');
console.log('    • รูปแบบการใช้: npm run analyze:deep');
console.log('    • ข้อมูลที่ได้: สถิติขั้นสูง, Correlation Matrix (ถ้ามี), และกราฟเชิงลึก (ถ้ากำหนดไว้)\n');

console.log('================================================================');
console.log('💡 Tip: หากต้องการตรวจสอบสาเหตุที่ออเดอร์ไม่เปิด ให้เริ่มจาก "npm run analytics:gate"');
console.log('================================================================\n');
