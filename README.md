# Interceptors

A storm-chasing addon for [ProtoManly's Weather](https://modrinth.com/mod/protomanlys-weather) (PMWeather) on
NeoForge 1.21.1. Drive tornado intercept vehicles into the storm, drop the anchors, and watch the tornado on your screens.

Needs **PMWeather 0.17.13 or newer**. Works next to the Tornado Intercept addon.

## Vehicles

| Vehicle | Armor | Slides at | Anchored holds | Body rated | Notes |
|---|---|---|---|---|---|
| Gaming Camper Van | 60 | 75 mph | 115 mph | 100 mph | Frantic Dan-style white high-roof camper. Fastest, RGB gaming lights, leveling jacks. |
| Dominator 1 (Slide 1) | 120 | 95 mph | 160 mph | 150 mph | Reed Timmer's armored Tahoe. Push bar, weather mast, ground spikes. |
| Dominator 2 | 160 | 110 mph | 185 mph | 175 mph | Longer armored Yukon XL. Side skirts drop to the road with the anchors. |
| Dominator 3 (Fail 3) | 220 | 130 mph | 230 mph | 220 mph | The big angular armored truck: skirts plus three pairs of spikes. |
| TIV 1 | 200 | 120 mph | 200 mph | 200 mph | The first Tornado Intercept Vehicle. Slowest, louvered armor glass, 2 seats. |
| Doppler on Wheels | 90 | 85 mph | 130 mph | 115 mph | Radar truck with a spinning dish; screens inside see twice as far. 2 seats. |
| Rental Car | 30 | 55 mph | no anchors | 70 mph | For when your interceptor is wrecked and you're stranded. Cheap, keeps you chasing. |

The Gaming Camper Van is hollow inside: wood paneling, a gaming desk with a big monitor and RGB tower, a sofa bed,
kitchenette and fridge, red SUBSCRIBE lettering on the sides, and a roof solar battery that runs its screens (it
drains while someone's inside and recharges in the sun).

- **Slides at**: wind the tires can handle. Above it the vehicle gets pushed, and at about 1.5x it starts to lift off.
- **Anchored holds**: the same with the anchors down.
- **Body rated**: above this the wind damages the vehicle, anchors or not. Fix it by right-clicking with iron ingots.

## Things break (like in the videos)

Strong wind and hail wear down five parts. The dashboard shows a warning light before a part fails.

| Part | When it fails |
|---|---|
| Hydraulics | Anchors and skirts won't drop, and pop back up if they were down. |
| Tires | Flat: you crawl along at 40% speed. |
| Engine | Sputters when worn; stalls when failed (you can only roll and brake). |
| Windows | Wind and debris come in and hurt everyone inside. |
| Computer | The dash and car display go dark and the anchors can't be used. |

The anchor computer can also **freeze** in a storm (the Dominator 3 three times as often, like in "We Were Hit,
Stranded, and got Tornado of the Year"). Frozen means the anchors can't move until you **hold G for 2 seconds**
to reboot it, which takes another 5 seconds. Hope the tornado isn't too close.

The **DROP ANCHOR** overlay (top right, copied from the Dominator 3's) draws a wireframe of your vehicle and its
anchor points: yellow STANDBY while moving, green READY, yellow DEPLOYING as the spikes drive into the ground,
green ANCHORED, and red ERROR when the computer is frozen or the hydraulics are broken or full of mud. Anchors
take 1.5 to 2.5 seconds to go down.

**Mud:** driving on dirt, mud, sand or gravel (twice as fast in the rain), and dropping anchors into soft ground,
packs mud into the hydraulics. A warning shows at 60%; at 100% they jam. Drive through water or use a repair kit
to clean them.

**Seats:** every vehicle seats 3 to 5 (driver, front passenger, back row). In a storm, nobody gets pulled out of
their seat: you only leave by pressing Shift, or if the vehicle is wrecked. Intact windows also stop debris.
Every vehicle has a live dash screen in front of the driver and a rugged laptop mounted for the front passenger;
the **Rugged Laptop** item opens the same storm apps anywhere.

Fix parts with a **Field Repair Kit** (right-click the vehicle; fully fixes the worst part and cleans out mud). Fix body damage
with iron ingots. Turn failures down or off in `interceptors-server.toml` (`failures.enabled`, `wearRate`,
`freezeRate`).

## Storms you can feel (2.0)

- **Wind roar:** a deafening roar that grows as the tornadic wind around you rises, drowning everything out near
  the core (a little quieter inside a vehicle with intact windows).
- **Baseball-size hail:** hail from PMWeather clatters on the roof, fogs the view in and streaks across the screen,
  dents vehicles, and hurts anyone caught outside under open sky.
- **Visible damage:** windows crack and then shatter, paint and armor get dented as the body takes damage, and
  below 30% armor the trim and stripes are torn off.
- **Satellite dish:** right-click a vehicle with a Satellite Dish to put one on the roof. Without it the online
  apps (Radar, Storms, Web) on screens inside show NO SIGNAL; the Intercept arrow and dash keep working. The
  Doppler on Wheels' radar counts as a dish.
- **Batteries:** the MacBook, iPad and Rugged Laptop drain while open (about 8 minutes of screen time) and charge
  while you ride in any interceptor (the van's charger draws on its solar battery).
- **Stream overlay:** press **B** for a full storm-chase livestream layout, made for OBS: an orange top bar, the
  game framed in orange and black with your crew name, a TORNADO WATCH box (red TORNADO WARNING with a tornado
  within 3 km) listing the nearby biomes as counties, a right column with a velocity radar of the real storms, a
  follower counter and viewer chat, and a bottom strip with the watch number and its countdown, the in-game date
  and time, and a scrolling ticker of the live storm data.

## Controls

| Key | Does |
|---|---|
| Right-click vehicle | Get in (2 or 4 seats; the first one in drives) |
| W / S | Gas / brake and reverse |
| A / D | Steer |
| Shift | Get out |
| G | Anchors down / up (stop first). Hold it to reboot a frozen computer |
| H | Horn |
| Y (or right-click with an empty hand from your seat) | Driver: car display. Passengers: the mounted passenger laptop (all apps, browser too) |
| U | Underglow lights on / off |
| B | Storm-chase livestream overlay on / off |
| Sneak + punch | Pack the vehicle back into its item |

All keys can be changed under Options > Controls > Interceptors. They are normal Minecraft keys, so Vivecraft can map
them to VR controller buttons.

## Screens

The computer and laptop blocks, the MacBook and iPad items, and the car display all run StormOS:

- **Radar**: live rain and hail from PMWeather, storms, and tornadoes with their 2-minute forecast track. Scroll to zoom.
- **Intercept**: a big arrow to the nearest tornado, its distance, EF rating, how fast it's coming at you and when it
  arrives, and a banner from KEEP CHASING to YOU ARE IN THE TORNADO.
- **Storms**: every storm, tornadoes first.
- **Tornado Dodge**: a mini game (on computers, laptops, tablets and the van's car display).
- **Web**: with the optional MCEF mod installed, a real web browser: back, forward, reload, home, an address bar
  that also searches Google, quick links (YouTube, NWS radar, SPC), and mouse, scroll and keyboard straight
  through to the page. Without MCEF it tells you what to install.

Every app has a letter on its dock icon that is also its keyboard shortcut: **V**ehicle, **R**adar, **I**ntercept,
**S**torms, **T**ornado Dodge, **W**eb (1-9 and Tab work too).

While driving, the dashboard (top left) shows speed, armor, anchors, the wind where you are, and the nearest tornado:
its EF rating, wind speed, distance and direction, and which way it's moving.

## Building

```
./gradlew build
```

builds `build/libs/interceptors-<version>.jar` and runs the GameTests in a headless server first.
PMWeather is closed source, so it isn't in this repo: put a copy of its jar in `libs/` (gitignored) and point
`pmweather_jar` in `gradle.properties` at it. Built and tested against PMWeather 0.17.16-alpha. Textures and sounds are generated by `tools/gen_assets.py`.

## License

MIT. PMWeather is All Rights Reserved by ProtoManly and is only compiled against, never included.
