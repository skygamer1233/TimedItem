# ⏳ TimedItem

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21%20%7C%201.21.1-brightgreen.svg)](https://papermc.io/)
[![Version](https://img.shields.io/badge/Version-1.1.0-blue.svg)](https://github.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)


---

> 🌐 **Language / Ngôn ngữ:**  
> **[🇻🇳 Tiếng Việt](#-tiếng-việt)** • **[🇬🇧 English](#-english)**

---

# 🇻🇳 Tiếng Việt

**TimedItem** là plugin Minecraft hiện đại, hiệu năng cao dành cho Spigot, Paper và Purpur (1.21+) giúp bạn tạo và quản lý các trang bị, vũ khí, danh hiệu hoặc vật phẩm đặc biệt có **Hạn Sử Dụng (Expiration Time)**.

Plugin được thiết kế tối ưu, xử lý **Bất đồng bộ (Asynchronous)** triệt để, không gián đoạn TPS server, tích hợp cơ chế tự động đọc/ghi dữ liệu người chơi offline và cơ chế kích hoạt thời hạn thông minh khi vật phẩm nằm trong kho đồ ở chế độ Sinh tồn (`Survival`).

---

## 🌟 Các Tính Năng Nổi Bật (Version 1.1.0)

### 1. ⚔️ MMOItems Hook (Mới trong v1.1.0)
* Tích hợp mượt mà với **MMOItems** thông qua cơ chế Soft-dependency an toàn (hoàn toàn không lỗi nếu server không cài MMOItems).
* Lệnh `/ti givemmo <player> <type> <id> <time>` cho phép Console, Admin hoặc các plugin khác (Crates, Quest, Shop, Quà nạp) tạo và phát trực tiếp item custom từ kho MMOItems kèm hạn sử dụng.
* **Tự động xử lý túi đồ đầy:** Nếu túi đồ của người chơi bị đầy, vật phẩm sẽ tự động rơi tại chân người chơi (`dropItemNaturally`) và phát thông báo.
* **Kích hoạt tức thì nếu ở Survival:** Nếu người chơi đang ở chế độ Sinh tồn, item sẽ lập tức kích hoạt thời gian đếm ngược.

### 2. 🧩 Hỗ Trợ PlaceholderAPI (PAPI Expansion)
Tích hợp sẵn expansion `%timeditem_...%` giúp bạn dễ dàng hiển thị thông tin thời hạn của vật phẩm đang cầm lên **Scoreboard, TAB list, Actionbar, Chat, DeluxeMenus, ConditionalEvents**:
* `%timeditem_hand_is_timed%`: Trả về `true` hoặc `false` (item trên tay có hạn sử dụng không).
* `%timeditem_hand_status%`: Trả về `active` (đang chạy), `inactive` (chờ kích hoạt), `expired` (đã hết hạn), hoặc `none`.
* `%timeditem_hand_status_formatted%`: Trạng thái có màu sắc từ file ngôn ngữ (ví dụ: `§aĐang hoạt động`, `§eChưa kích hoạt`).
* `%timeditem_hand_remaining%`: Thời gian còn lại hiển thị đẹp mắt (ví dụ: `2d 5h 30m`, `45m 12s`).
* `%timeditem_hand_remaining_seconds%`: Thời gian còn lại tính bằng giây (dạng số nguyên, lý tưởng cho các điều kiện logic kiểm tra trong menu).
* `%timeditem_hand_expiry%`: Ngày giờ hết hạn chuẩn định dạng (ví dụ: `25/12/2026 18:30:00`).
* `%timeditem_hand_name%`: Tên hiển thị của vật phẩm trên tay.

### 3. 🎫 Phiếu Gia Hạn Thời Gian (Extension Tokens)
* Tạo ra các vật phẩm tiêu hao (Tokens) dùng để kéo dài thời gian sử dụng cho bất kỳ trang bị có hạn nào.
* **Cơ chế Kéo & Thả (Drag & Drop):** Người chơi chỉ cần nhặt Phiếu Gia Hạn trong túi đồ và nhấp chuột trực tiếp lên trang bị có hạn để nạp thêm thời gian!
* Hiệu ứng âm thanh hấp dẫn (`BLOCK_ENCHANTMENT_TABLE_USE` & `ENTITY_PLAYER_LEVELUP`) kèm thông báo rõ ràng về hạn sử dụng mới.
* Tùy biến toàn diện trong `config.yml`: Material, Tên hiển thị, Lore, Hiệu ứng phát sáng (Glow), CustomModelData.

### 4. 🌐 Đa Ngôn Ngữ Song Ngữ (Tiếng Việt & English)
* Hỗ trợ chuyển đổi nhanh chóng trong `config.yml` với `language: "vi"` hoặc `language: "en"`.
* Cung cấp sẵn hai bộ ngôn ngữ hoàn chỉnh: `lang_vi.yml` và `lang_en.yml`.
* Tự động điều chỉnh cả dòng Lore trên item và toàn bộ thông báo hệ thống theo ngôn ngữ đã chọn.

### 5. 🛡️ Cơ Chế 2 Trạng Thái Thông Minh
* **Trạng thái Chờ (`Hiệu lực: <thời gian>`):** Khi tạo bằng lệnh, vật phẩm chưa tính giờ; rất thích hợp đặt vào Crate, Shop, BattlePass, Kit hoặc rương đồ. Admin ở chế độ Sáng tạo (`Creative`) có thể di chuyển, nhân bản vật phẩm mà không sợ kích hoạt nhầm.
* **Trạng thái Kích hoạt (`Hết hạn vào: <ngày giờ>`):** Tự động kích hoạt khi người chơi sinh tồn cầm hoặc nhận vào kho đồ.

### 6. 💤 Quét Kho Đồ Người Chơi Offline
* Tích hợp bộ đọc/ghi file NBT (`playerdata/<uuid>.dat`) độc lập viết bằng Java thuần.
* Tự động xóa sạch đồ hết hạn trong túi và Rương Ender của người chơi offline ngầm (Asynchronous 100%, 0% drop TPS).
* Gửi thông báo tổng kết khi người chơi đăng nhập lại.

---

## 💻 Hệ Thống Lệnh (Commands)

| Lệnh | Quyền hạn (Permission) | Mô tả & Ví dụ |
| :--- | :--- | :--- |
| `/ti add [thời gian]` | `timeditem.command.add` | Thêm thời gian hiệu lực cho item trên tay (VD: `/ti add 1d2h`, `/ti add 30m`) |
| `/ti remove [thời gian]` | `timeditem.command.remove` | Trừ bớt thời gian của item (VD: `/ti remove 3d5h`) |
| `/ti set <định dạng>` | `timeditem.command.set` | Đặt thời gian hiệu lực hoặc ấn định ngày giờ hết hạn (VD: `/ti set 12/07/2026`) |
| `/ti clear` | `timeditem.command.clear` | Xóa bỏ hoàn toàn thời hạn khỏi item trên tay |
| `/ti check` | `timeditem.command.check` | Xem thông tin chi tiết về thời hạn, trạng thái của item trên tay |
| `/ti givemmo <người chơi> <loại> <id> <thời gian>` | `timeditem.command.givemmo` | Phát item MMOItems kèm thời hạn (VD: `/ti givemmo Steve SWORD CUTLASS 7d`) |
| `/ti token <thời gian> [số lượng] [người chơi]` | `timeditem.command.token` | Tạo hoặc tặng Phiếu Gia Hạn (VD: `/ti token 3d 1 Steve` hoặc `/ti token 1d`) |
| `/ti reload` | `timeditem.command.reload` | Nạp lại cấu hình và ngôn ngữ |
| `/ti help [trang]` | `timeditem.command.help` | Xem danh sách hướng dẫn lệnh |

---

## 🔑 Bảng Quyền Hạn (Permissions)

* `timeditem.command.add` — Dùng lệnh `/ti add` *(Mặc định: OP)*
* `timeditem.command.remove` — Dùng lệnh `/ti remove` *(Mặc định: OP)*
* `timeditem.command.set` — Dùng lệnh `/ti set` *(Mặc định: OP)*
* `timeditem.command.clear` — Dùng lệnh `/ti clear` *(Mặc định: OP)*
* `timeditem.command.check` — Dùng lệnh `/ti check` *(Mặc định: Mọi người)*
* `timeditem.command.givemmo` — Dùng lệnh `/ti givemmo` *(Mặc định: OP)*
* `timeditem.command.token` — Dùng lệnh `/ti token` *(Mặc định: OP)*
* `timeditem.command.reload` — Dùng lệnh `/ti reload` *(Mặc định: OP)*
* `timeditem.command.help` — Dùng lệnh `/ti help` *(Mặc định: Mọi người)*

---

# 🇬🇧 English

**TimedItem** is a next-generation, high-performance item expiration management plugin for Spigot, Paper, and Purpur (1.21+). It allows server administrators to create temporary equipment, weapons, armor, badges, and rewards with exact expiration dates or countdown timers.

Built with **100% Asynchronous processing**, TimedItem guarantees **Zero TPS drops**, pure Java offline playerdata NBT reading/writing, and a smart dual-state activation mechanic for Survival mode.

---

## 🌟 Key Features (v1.1.0)

### 1. ⚔️ MMOItems Integration (`/ti givemmo`)
* Seamless soft-dependency with **MMOItems** using safe runtime reflection. No crash or errors if MMOItems is absent.
* Easily give timed MMOItems via Console or In-game: `/ti givemmo <player> <type> <id> <time>` (e.g. `/ti givemmo Steve SWORD CUTLASS 7d`).
* **Inventory Overflow Safe:** Automatically drops items naturally at player's location if the inventory is full.
* **Instant Survival Activation:** Immediately starts counting down if given to players in Survival mode.

### 2. 🧩 PlaceholderAPI Expansion
Expose real-time item expiration variables across your server (Scoreboard, TAB, Actionbar, DeluxeMenus, ConditionalEvents):
* `%timeditem_hand_is_timed%`: Returns `true` or `false`.
* `%timeditem_hand_status%`: Returns `active`, `inactive`, `expired`, or `none`.
* `%timeditem_hand_status_formatted%`: Localized colored status string (e.g. `§aActive`).
* `%timeditem_hand_remaining%`: Formatted countdown string (e.g. `2d 5h 30m`).
* `%timeditem_hand_remaining_seconds%`: Exact seconds remaining as an integer (perfect for GUI requirement checks).
* `%timeditem_hand_expiry%`: Formatted expiration date (`dd/MM/yyyy HH:mm:ss`).
* `%timeditem_hand_name%`: Held item display name.

### 3. 🎫 Time Extension Tokens
* Issue consumable extension tokens via `/ti token <time> [amount] [player]`.
* **Intuitive Drag-and-Drop:** Players grab the token and click directly onto any timed item in their inventory to extend its expiration!
* Plays level-up and enchantment table sound effects upon successful extension.
* Fully customizable token item material, name, lore, glow, and custom-model-data in `config.yml`.

### 4. 🌐 Bilingual Engine (English & Vietnamese)
* Switch between languages with a single toggle: `language: "en"` or `language: "vi"`.
* Ships with complete `lang_en.yml` and `lang_vi.yml` translation files.
* `/ti reload` applies configuration and language updates immediately without restarting the server.

### 5. 🛡️ Smart Dual-State Engine
* **Inactive State (`Duration: <time>`):** Countdown does NOT tick down when items are stored in Crates, Shops, Kits, or reward GUIs. Creative mode admins can handle items safely.
* **Active State (`Expires at: <date>`):** Automatically triggers and begins counting down when an item is placed in a player's inventory in **Survival mode**.

### 6. 💤 100% Async Offline Player NBT Scanner
* Scans `world/playerdata/*.dat` files completely off the main server thread.
* Automatically strips expired items from both **Player Inventories** and **Ender Chests** while players are offline.
* Notifies returning players with a summary message upon login.

---

## 💻 Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ti add [time]` | `timeditem.command.add` | Add duration to held item (ex: `/ti add 1d2h`, `/ti add 30m`) |
| `/ti remove [time]` | `timeditem.command.remove` | Remove duration from item (ex: `/ti remove 3d5h`) |
| `/ti set <format>` | `timeditem.command.set` | Set duration or exact date (ex: `/ti set 12/07/2026`) |
| `/ti clear` | `timeditem.command.clear` | Remove expiration from held item |
| `/ti check` | `timeditem.command.check` | Inspect held item's expiration status |
| `/ti givemmo <player> <type> <id> <time>` | `timeditem.command.givemmo` | Give timed MMOItems item (ex: `/ti givemmo Steve SWORD CUTLASS 7d`) |
| `/ti token <time> [amount] [player]` | `timeditem.command.token` | Give Time Extension Tokens (ex: `/ti token 3d 1 Steve`) |
| `/ti reload` | `timeditem.command.reload` | Reload configuration and language files |
| `/ti help [page]` | `timeditem.command.help` | View paged help menu |

---

## ⚙️ Configuration Preview

### `config.yml`
```yaml
# ======================================================
#            TimedItem Configuration (v1.1.0)
# ======================================================

# Active language of the plugin:
#   "en" : English (loads lang/lang_en.yml)
#   "vi" : Vietnamese (loads lang/lang_vi.yml)
# If set to "vi", reloading the plugin (/ti reload) will
# automatically switch this configuration file to Vietnamese!
language: "en"

# Date format for commands and expiration display
# Default: dd/MM/yyyy HH:mm:ss
date-format: "dd/MM/yyyy HH:mm:ss"

# How often to check for expired items (in seconds)
# Affects both online inventory and world dropped checks
check-interval: 30

# Whether to check items dropped in the world
world-check: true

# Whether to check online player inventories
inventory-check: true

# Whether to check offline player inventories and Ender Chests (100% Async)
offline-check: true

# Number of items shown per help page (/ti help)
help-per-page: 7

# Technical settings for Extension Tokens (Phiếu Gia Hạn)
# Display name & lore of tokens are localized in lang/lang_en.yml or lang/lang_vi.yml
token:
  material: PAPER
  custom-model-data: 0
  glow: true

```
