extends SceneTree
## Refreshes data_samples/<tool>.json with live results from the same sources as the app's tools
## (info_sources.gd), so the info scenes and the recordings show current, real numbers:
##   Godot --headless --path pet_godot --script res://tools/save_samples.gd
## A source that doesn't answer keeps its old file.

const TOOLS := ["device_weather", "trading_fundamental_analysis", "trading_price", "trading_technical_analysis",
	"trading_top_gainers", "trading_top_losers", "trading_fear_greed", "trading_crypto_overview"]


func _initialize() -> void:
	var info: Node = (load("res://scripts/info_sources.gd") as GDScript).new()
	root.add_child(info)
	await process_frame
	var failed := 0
	for tool in TOOLS:
		var r = await info.fetch(tool)
		if (r is Dictionary or r is Array) and not r.is_empty():
			var f := FileAccess.open("res://data_samples/%s.json" % tool, FileAccess.WRITE)
			f.store_string(JSON.stringify(r, "  "))
			f.close()
			print("SAVED ", tool)
		else:
			failed += 1
			print("FAILED ", tool, " (kept the old file)")
	quit(1 if failed else 0)
