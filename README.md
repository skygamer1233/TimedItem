# ⏳ TimedItem - Advanced Item Duration & Expiration Management

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21+-brightgreen.svg" alt="Minecraft Version" />
  <img src="https://img.shields.io/badge/Java-21+-orange.svg" alt="Java Version" />
  <img src="https://img.shields.io/badge/Version-1.2.0-blue.svg" alt="Plugin Version" />
  <img src="https://img.shields.io/badge/Hooks-MMOItems%20%7C%20ItemsAdder%20%7C%20Oraxen%20%7C%20Nexo%20%7C%20EI-purple.svg" alt="Custom Item Hooks" />
  <img src="https://img.shields.io/badge/PlaceholderAPI-Supported-yellow.svg" alt="PAPI Hook" />
</p>

**TimedItem** is a high-performance, feature-packed Spigot/Paper plugin designed for Minecraft servers running **1.21+** (Java 21). It allows server administrators and creators to assign temporary validity or exact expiration dates to any in-game item — whether vanilla Minecraft, saved templates, or custom items from **MMOItems**, **ItemsAdder**, **Oraxen**, **Nexo**, or **ExecutableItems**.

---

## 🌟 Major Highlights & New Features in v1.2.0

### 1. 🔌 Universal Custom Item Ecosystem & Internal Template Storage (`/ti save`)
* **Unified `/ti give` Command:** Give Vanilla or Custom Items directly with full tab-completion:
  * **Vanilla:** `/ti give <player> DIAMOND_SWORD 7d 1`
  * **ItemsAdder:** `/ti give <player> ia:myitems:ruby_sword 7d 1`
  * **Oraxen:** `/ti give <player> oraxen:katana 7d 1`
  * **Nexo:** `/ti give <player> nexo:frost_blade 7d 1`
  * **ExecutableItems:** `/ti give <player> ei:magic_wand 7d 1`
  * **MMOItems:** `/ti give <player> mmo:SWORD:CUTLASS 7d 1` (or `/ti givemmo <player> SWORD CUTLASS 7d`)
  * **Saved Templates:** `/ti give <player> saved:vip_sword 7d 1`
* **Internal Item Template Storage:** Save any in-hand item (from any plugin, with custom NBT/enchants/gems) via `/ti save <id>`, view via `/ti list`, and remove via `/ti delete <id>`.
* **Zero TPS Overhead:** Cached reflection handles and 30-second TTL tab-completion caches ensure 0% lag during gameplay or command tabbing.

### 2. 🔒 Dual Expire Action Modes (`DELETE` vs `SEAL`)
* **Flexible Administration (Global + Per-Item):**
  * Configure server-wide default in `config.yml`: `default-expire-action: "DELETE"` or `"SEAL"`.
  * Override per-item via `/ti mode <DELETE|SEAL>` or pass `[DELETE|SEAL]` at the end of `/ti add`, `/ti set`, `/ti give`, `/ti givemmo`.
* **`DELETE` Mode:** Automatically deletes the item and plays a break sound upon expiration.
* **`SEAL` Mode (Phong Ấn):**
  * Item is **kept** in the player's inventory when expired, marked with `[EXPIRED - SEALED]` lore.
  * Completely blocks attacking, shooting, mining, consuming, and interacting with sealed items.
  * Automatically unequips expired `SEAL` armor into the player's inventory (or drops at feet if full) so players cannot retain armor stats.
  * **Revivable via Extension Tokens:** Players can drag-and-drop an Extension Token onto a sealed item to unseal and extend it!

### 3. ⏰ Real-Time Expiry Warnings
* Configurable countdown milestones (`expiry-warning.times: [300, 60, 10]` — e.g., 5 minutes, 1 minute, 10 seconds before expiry).
* Alerts players holding or wearing expiring items via **Actionbar**, **Chat**, and **Sound** notifications.
* Tracked in-memory without modifying `ItemMeta`, preventing block-breaking resets or item-bobbing animations.

### 4. 🎫 Time Extension Tokens & Max Extension Limits
* Issue consumable extension tokens via `/ti token <time> [amount] [player]`.
* **Drag-and-Drop:** Click a token onto any timed or sealed item in inventory to extend its validity.
* **Max Extensions Limit (`token.max-extensions`):** Limit how many times an item can be extended via tokens (`0` = unlimited).

### 5. 🧩 PlaceholderAPI Expansion
* `%timeditem_hand_is_timed%`: `true` or `false`
* `%timeditem_hand_status%`: `active`, `inactive`, `expired`, `sealed`, or `none`
* `%timeditem_hand_status_formatted%`: Localized colored status string
* `%timeditem_hand_expire_action%`: `delete`, `seal`, or `none`
* `%timeditem_hand_expire_action_formatted%`: Localized colored action mode
* `%timeditem_hand_extensions_used%` & `%timeditem_hand_extensions_max%`: Token extension counters
* `%timeditem_hand_remaining%` & `%timeditem_hand_remaining_seconds%`
* `%timeditem_hand_expiry%` & `%timeditem_hand_name%`

---

## 💻 Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ti add <time> [DELETE\|SEAL]` | `timeditem.command.add` | Add duration to held item (ex: `/ti add 1d2h SEAL`) |
| `/ti remove <time>` | `timeditem.command.remove` | Remove duration from item (ex: `/ti remove 3d5h`) |
| `/ti set <format> [DELETE\|SEAL]` | `timeditem.command.set` | Set duration or exact date (ex: `/ti set 12/07/2026 SEAL`) |
| `/ti mode <DELETE\|SEAL>` | `timeditem.command.mode` | Change expire action mode for held timed item |
| `/ti clear` | `timeditem.command.clear` | Remove expiration from held item |
| `/ti check` | `timeditem.command.check` | Inspect held item's expiration status, mode & extensions |
| `/ti give <player> <item> <time> [amount] [DELETE\|SEAL]` | `timeditem.command.give` | Give timed item (`DIAMOND_SWORD`, `ia:...`, `oraxen:...`, `nexo:...`, `ei:...`, `mmo:...`, `saved:...`) |
| `/ti givemmo <player> <type> <id> <time> [DELETE\|SEAL]` | `timeditem.command.givemmo` | Give timed MMOItems item |
| `/ti save <id>` | `timeditem.command.save` | Save held item as a reusable template |
| `/ti delete <id>` | `timeditem.command.delete` | Delete a saved item template |
| `/ti list` | `timeditem.command.list` | List all saved item templates |
| `/ti token <time> [amount] [player]` | `timeditem.command.token` | Give Time Extension Tokens |
| `/ti reload` | `timeditem.command.reload` | Reload configuration, templates, and language files |
| `/ti help [page]` | `timeditem.command.help` | View paged help menu |

### Master Permissions
* `timeditem.*` : Grants full access to all TimedItem commands and permissions (default: `op`).
* `timeditem.admin` : Grants access to all administrative commands (default: `op`).

---

## 🛠️ Building & Requirements
* **Java:** 21 or higher.
* **Server Software:** Spigot, Paper, Purpur 1.21.1+.
* **Build Tool:** Maven (`mvn clean package`). Output jar located at `target/TimedItem-1.2.0.jar`.
