extends SceneTree
## Headless check of where things end up:  godot --headless --path pet_godot --script res://tools/inspect.gd
## Prints world-space bounds of the robot's main parts and of every accessory worn on it.

const PetCharacter := preload("res://scripts/pet_character.gd")


func _bounds(node: Node) -> AABB:
	var box := AABB()
	var first := true
	for m in node.find_children("*", "MeshInstance3D", true, false):
		var mi := m as MeshInstance3D
		var b := mi.global_transform * mi.get_aabb()
		box = b if first else box.merge(b)
		first = false
	if node is MeshInstance3D:
		var own := (node as MeshInstance3D).global_transform * (node as MeshInstance3D).get_aabb()
		box = own if first else box.merge(own)
	return box


func _fmt(b: AABB) -> String:
	var c := b.get_center()
	return "center (%.2f, %.2f, %.2f) size (%.2f, %.2f, %.2f)" % [c.x, c.y, c.z, b.size.x, b.size.y, b.size.z]


func _initialize() -> void:
	var pet := PetCharacter.new()
	root.add_child(pet)
	await process_frame
	pet.set_process(false)
	pet.find_child("AnimationPlayer", true, false).stop()
	for part in ["PR_Head", "PR_Screen", "PR_Vest", "PR_Belly", "PR_EarPivotL", "PR_EarPivotR"]:
		print("PART ", part, " ", _fmt(_bounds(pet.find_child(part, true, false))))
	# one item at a time (items in the same slot replace each other)
	for a in pet.items():
		pet.wear(a)
		await create_timer(0.6).timeout          # let the drop-in / grow tweens land
		print("WEAR ", a, " ", pet.info(a)["slot"], " ", _fmt(_bounds(pet.find_child("Wear_%s" % a, true, false))))
		pet.take_off(a)
	for p in ["PR_Float", "PR_HeadPivot", "PR_BodyPivot", "PR_HeadRoot"]:
		var n := pet.find_child(p, true, false) as Node3D
		print("NODE ", p, " global ", n.global_transform, " parent ", n.get_parent().name)
	quit()
