# Wonder Realms 2.0.1

A fix for pillager sky outposts.

## 🔧 Fixes

- **Pillagers' dragons can be freed again.** However many pillagers you cleared, an outpost's captive dragons stayed
  captive: the outpost was only ever checked once. Now it's checked every couple of seconds, so once the last pillager
  falls, its dragons are free and yours to tame
- **Only an outpost's own pillagers count**: the ones on its island and in the air round it (within about 32 blocks,
  and 20 up or down), the dragon riders and the airship crew included. Pillagers on another island nearby, above or
  below no longer keep it held
- **Try to tame a captive dragon too soon** and it tells you how many pillagers are left, and they **glow** for ten
  seconds so you can find them (look on the airship, and up in the sky on their dragons)

Outposts you've already cleared work straight away: their dragons are free as soon as you're back, ready to tame.
