extends SceneTree
## Checks that the live sources the lab's "ข้อมูล" tab uses still answer (network needed):
##   Godot --headless --path pet_godot --script res://tools/info_live_test.gd


func _initialize() -> void:
	var info: Node = (load("res://scripts/info_sources.gd") as GDScript).new()
	root.add_child(info)
	await process_frame                  # the node joins the tree on the next frame
	var w: Dictionary = await info.weather()
	print("WEATHER ", "ok " if w.get("temp") != null else "FAIL ", JSON.stringify(w))
	var s: Dictionary = await info.stock("NASDAQ:MSFT")
	print("STOCK ", "ok " if s.get("totalAssets") != null else "FAIL ", JSON.stringify(s))
	quit(0 if w.get("temp") != null and s.get("totalAssets") != null else 1)
