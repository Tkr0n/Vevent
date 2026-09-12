#Make the function run every 2 Ticks instead
scoreboard players add #tick simplerevive 1
execute if score #tick simplerevive matches 2.. run return run scoreboard players reset #tick simplerevive

# Setup time_since_death objective (runs once via sentinel)
execute unless score #tsd_setup simplerevive matches 1 run scoreboard objectives add simplerevive.time_since_death minecraft.custom:minecraft.time_since_death
execute unless score #tsd_setup simplerevive matches 1 run scoreboard players set #tsd_setup simplerevive 1

#
execute as @a at @s run function simplerevive:as_player
