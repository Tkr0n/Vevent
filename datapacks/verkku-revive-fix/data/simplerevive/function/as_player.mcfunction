scoreboard players remove @s[scores={simplerevive.cd=1..}] simplerevive.cd 1

execute if score @s simplerevive.hunger_ticks matches 0.. run function simplerevive:hunger

# IMPORTANT: time_since_death check MUST come before as_downed check.
# When fail.mcfunction calls kill @s, the player respawns but still has the downed tag.
# If as_downed runs first, it sees downed_time=0 and calls fail again -> infinite loop.
# down_me handles the case where the player already has the downed tag (calls undo_downed).
execute if score @s simplerevive.time_since_death matches 0..1 run function simplerevive:downed/down_me

execute if entity @s[tag=simplerevive.downed] run function simplerevive:downed/as_downed

execute if score @s simplerevive.toggle_downed matches 1.. run function simplerevive:toggle_downed
