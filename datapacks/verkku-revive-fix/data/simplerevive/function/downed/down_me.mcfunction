#Reset Scoreboard
scoreboard players reset @s simplerevive.deathCount
scoreboard players reset @s simplerevive.leave_game
scoreboard players set @s simplerevive.time_since_death 999

#Condition -> Return
execute if entity @s[tag=simplerevive.downed] run return run function simplerevive:downed/undo_downed
execute if score @s simplerevive.cd matches 1.. run return run tellraw @s "You died too quickly to get Downed"
execute if entity @s[gamemode=!survival,gamemode=!adventure] run return fail
execute if entity @s[tag=simplerevive.custom_disable] run return run tellraw @s "Another mod prevented you from getting downed"
execute if entity @s[tag=simplerevive.self_disable] run return run tellraw @s "You couldn't get downed because you disabled it, to revert this run '/trigger simplerevive.toggle_downed'"

scoreboard players enable @s simplerevive.toggle_downed

#Initiate Downed
tag @s add simplerevive.downed
scoreboard players add #main simplerevive.id 1
scoreboard players operation @s simplerevive.id = #main simplerevive.id

data modify storage simplerevive:temp LastDeathLocation set from entity @s LastDeathLocation
data modify storage simplerevive:temp LastDeathLocation.x set from storage simplerevive:temp LastDeathLocation.pos[0]
data modify storage simplerevive:temp LastDeathLocation.y set from storage simplerevive:temp LastDeathLocation.pos[1]
data modify storage simplerevive:temp LastDeathLocation.z set from storage simplerevive:temp LastDeathLocation.pos[2]

tag @s add simplerevive.temp.player
execute summon marker run function simplerevive:downed/down_me_marker with storage simplerevive:temp LastDeathLocation
tag @s remove simplerevive.temp.player

#
execute store result score #show_death_messages simplerevive run gamerule show_death_messages
execute if score #show_death_messages simplerevive matches 1 run tellraw @a [{selector:"@s"},{translate:"simplerevive.has_been_downed",fallback:" has been downed"}]
#
title @s actionbar {translate:"simplerevive.give_up",color:"white",fallback:"Look Down and Hold Sneak to Give Up"}
