# ⏳ TimedItem

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21%20%7C%201.21.1-brightgreen.svg)](https://papermc.io/)
[![Build](https://img.shields.io/badge/Maven-Build%20Passing-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**TimedItem** là plugin Minecraft hiện đại dành cho Spigot, Paper và Purpur (1.21+) giúp bạn tạo ra các trang bị, vũ khí, danh hiệu hoặc vật phẩm đặc biệt có **Hạn Sử Dụng (Expiration Time)**.

Plugin được thiết kế tối ưu, xử lý **Bất đồng bộ (Asynchronous)** triệt để, không gián đoạn TPS server, tích hợp cơ chế tự động đọc/ghi dữ liệu người chơi offline và cơ chế kích hoạt thời hạn thông minh khi vật phẩm nằm trong kho đồ ở chế độ Sinh tồn (`Survival`).

---

## 🌟 Các Tính Năng Nổi Bật

### 1. 🛡️ Cơ Chế 2 Trạng Thái Thông Minh (Chờ kích hoạt & Đang tính giờ)
* **Trạng thái Chờ (`Hiệu lực: <thời gian>`):**
  * Khi tạo vật phẩm bằng lệnh, vật phẩm ở trạng thái chờ: `Hiệu lực: 1d 2h` (chưa đếm ngược).
  * **Rất thích hợp để làm phần thưởng:** Bạn có thể thoải mái đặt item vào **Hòm báu (Crate), Cửa hàng (Shop), Quà nạp, BattlePass, Kit** hoặc cất trong rương mà **không bị mất hạn**.
  * Admin ở chế độ Sáng tạo (`Creative`) có thể tự do cầm, di chuyển, nhân bản vật phẩm mà không kích hoạt nhầm.
* **Trạng thái Kích hoạt (`Hết hạn vào: <ngày giờ>`):**
  * Ngay khi vật phẩm xuất hiện trong kho đồ của người chơi ở chế độ **Sinh tồn (`Survival`)** (nhặt từ đất, lấy từ rương/GUI, hoặc chuyển gamemode), plugin sẽ:
    1. Phát âm thanh *Pling* và gửi thông báo kích hoạt đến người chơi.
    2. Chuyển dòng lore sang `Hết hạn vào: <ngày giờ>`.
    3. Bắt đầu đếm ngược thời gian chính xác từ thời điểm đó!

---

### 2. 💤 Quét Kho Đồ Người Chơi Offline (Offline Player Check)
* **Tự động xóa đồ hết hạn khi người chơi offline:**
  * Tích hợp bộ đọc/ghi file NBT (`playerdata/<uuid>.dat`) độc lập viết bằng Java thuần (không phụ thuộc NMS, không lo lỗi khi cập nhật phiên bản).
  * Tự động kiểm tra cả **Túi đồ (Inventory)** và **Rương Ender (Ender Chest)** của người chơi offline.
* **Thông báo khi người chơi đăng nhập lại:**
  * Số lượng vật phẩm bị xóa trong lúc offline được lưu trữ an toàn.
  * Khi người chơi vào lại server, hệ thống sẽ gửi thông báo: *“Khi bạn đang offline, có X vật phẩm đã hết hạn và tự động bị xóa!”*.

---

### 3. ⚡ Bất Đồng Bộ 100% (Async Check — 0% Lag TPS)
* **Bảo vệ hiệu năng server tuyệt đối:**
  * Toàn bộ tác vụ đọc/ghi file đĩa người chơi offline chạy ngầm ở luồng **Asynchronous**, loại bỏ hoàn toàn hiện tượng drop TPS.
  * Các thao tác kiểm tra kho đồ người chơi online và vật phẩm rơi ngoài sàn được đồng bộ an toàn (Thread-safe) theo chuẩn Spigot/Paper.
* **Kiểm tra mặt đất (`World Check`):**
  * Tự động quét và dọn dẹp các vật phẩm có thời hạn bị vứt rơi trên sàn thế giới nếu chúng đã hết hạn.
* **Bắt sự kiện thời gian thực (Real-time Event):**
  * Khi người chơi click vào item trong kho, tương tác chuột, hoặc nhặt item từ đất, hệ thống sẽ kiểm tra ngay lập tức. Nếu item vừa hết hạn, nó sẽ biến mất ngay và phát âm thanh vỡ đồ.

---

### 4. 🎨 Quản Lý Dữ Liệu & Lore Chuẩn Xác
* Sử dụng hệ thống **`PersistentDataContainer` (PDC)** chuẩn của Minecraft để lưu trữ timestamp.
* **Không làm hỏng Lore khác:** Tự động nhận diện và cập nhật đúng dòng hạn sử dụng mà không làm mất Enchantment, dòng thuộc tính MMORPG, Custom Lore của các plugin khác.
* **Hỗ trợ màu sắc đa dạng:** Hỗ trợ toàn bộ mã màu Minecraft truyền thống `&a, &e, &c...` và mã màu **Hex Color** dạng `&#RRGGBB`.

---

## 💻 Hệ Thống Lệnh (Commands)

| Lệnh | Quyền hạn (Permission) | Mô tả & Ví dụ |
| :--- | :--- | :--- |
| `/ti add [thời gian]` | `timeditem.command.add` | Thêm thời gian hiệu lực cho item trên tay (VD: `/ti add 1d2h`, `/ti add 30m`) |
| `/ti remove [thời gian]` | `timeditem.command.remove` | Trừ bớt thời gian của item (VD: `/ti remove 3d5h`). Nếu trừ về quá khứ sẽ tự xoá. |
| `/ti set <định dạng>` | `timeditem.command.set` | Đặt thời gian hiệu lực hoặc ấn định ngày giờ hết hạn (VD: `/ti set 1d2h`, `/ti set 12/07/2026`, `/ti set 27/05/2026 13:17:29`) |
| `/ti clear` | `timeditem.command.clear` | Xóa bỏ hoàn toàn thời hạn khỏi item trên tay |
| `/ti reload` | `timeditem.command.reload` | Nạp lại cấu hình plugin (`config.yml` & `lang.yml`) |
| `/ti help <trang>` | `timeditem.command.help` | Xem danh sách hướng dẫn phân trang |

> 💡 **Hỗ trợ Tab-Completion:** Tự động gợi ý lệnh con, các mốc thời gian phổ biến (`1h`, `1d`, `7d`, `30d`...) và số trang.

### ⏱️ Các đơn vị thời gian hỗ trợ:
* `s` / `sec`: Giây
* `m` / `min`: Phút
* `h` / `hr`: Giờ
* `d` / `day`: Ngày
* `w` / `week`: Tuần
* `mo` / `month`: Tháng (30 ngày)
* `y` / `year`: Năm (365 ngày)
* *Có thể ghép lại:* `1y2mo3w4d5h30m10s`, `1d2h`, `3d5h`...

---

## 🔑 Quyền Hạn (Permissions)

* `timeditem.command.add` — Quyền sử dụng lệnh `/ti add` *(Mặc định: OP)*
* `timeditem.command.remove` — Quyền sử dụng lệnh `/ti remove` *(Mặc định: OP)*
* `timeditem.command.set` — Quyền sử dụng lệnh `/ti set` *(Mặc định: OP)*
* `timeditem.command.clear` — Quyền sử dụng lệnh `/ti clear` *(Mặc định: OP)*
* `timeditem.command.reload` — Quyền sử dụng lệnh `/ti reload` *(Mặc định: OP)*
* `timeditem.command.help` — Quyền xem menu trợ giúp `/ti help` *(Mặc định: Mọi người)*

---

## ⚙️ Cấu Hình Mẫu

### 📄 `config.yml`
```yaml
# Định dạng ngày giờ mặc định cho lệnh /ti set
date-format: "dd/MM/yyyy HH:mm:ss"

# Chu kỳ kiểm tra vật phẩm hết hạn (tính bằng giây)
check-interval: 30

# Bật/tắt kiểm tra vật phẩm rơi ngoài sàn
world-check: true

# Bật/tắt kiểm tra kho đồ người chơi online
inventory-check: true

# Bật/tắt kiểm tra kho đồ người chơi offline
offline-check: true

# Số lượng lệnh hiển thị mỗi trang help
help-per-page: 7

# Dòng lore khi vật phẩm CHƯA kích hoạt (chưa tính giờ)
lore-inactive-format: "&eHiệu lực: &a{duration}"

# Dòng lore khi vật phẩm ĐÃ kích hoạt (đang tính giờ)
lore-format: "&cHết hạn vào: &e{expiry}"
```

### 📄 `lang.yml` (Tùy biến 100% ngôn ngữ)
```yaml
prefix: "&8[&6TimedItem&8] &r"
item-expired: "&cVật phẩm &e{item} &cx{amount} &cđã hết hạn và đã tự xoá khỏi kho!"
items-expired-offline: "&eKhi bạn đang offline, có &c{count} &evật phẩmđã hết hạn và đã tự xoá khỏi kho!"
item-activated: "&aVật phẩm &e{item} &ađã kích hoạt! Hạn sử dụng: &e{expiry}"
duration-set: "&aĐã đặt thời gian hiệu lực &e{duration} &acho vật phẩm (sẽ tính giờ khi cầm trên tay ở chế độ sinh tồn)."
time-added: "&aĐã thêm &e{time} &avào vật phẩm. Hạn sử dụng mới: &e{expiry}"
time-removed: "&aĐã xoá &e{time} &akhỏi vật phẩm. Hạn sử dụng mới: &e{expiry}"
time-set: "&aĐã set thời hạn thành &e{expiry}&a."
time-cleared: "&aĐã xoá thời hạn cho item."
no-item-in-hand: "&cBạn phải cầm item trên tay!"
not-timed-item: "&cItem này không có thời hạn!"
invalid-time-format: "&cSai định dạng thời gian! Ví dụ: 1d2h30m"
invalid-date-format: "&cSai định dạng thời gian! Ví dụ: {format}"
date-in-past: "&cBạn phải set ngày ở tương lai!"
no-permission: "&cBạn không có quyền dùng lệnh này này!"
reloaded: "&aĐã nạp lại plugin thành công!"
player-only: "&cBạn phải là player!"
usage: "&cCách dùng: {usage}"
help-header: "&8&m----------&r &6Hướng Dẫn TimedItem &7(Trang {page}/{max_page}) &8&m----------"
help-footer: "&8&m----------&r &7Dùng /ti help {next_page} để qua trang kế tiếp &8&m----------"

# Toàn bộ danh sách lệnh trong /ti help đều có thể tùy chỉnh tại đây:
help-commands:
  add: "&6/ti add [time] &7- Thêm thời hạn cho vật phẩm. &8(vd: /ti add 1d2h)"
  remove: "&6/ti remove [time] &7- Xoá bớt thời hạn của vật phẩm. &8(vd: /ti remove 3d5h)"
  set: "&6/ti set <format> &7- Đặt thời hạn cho vật phẩm. &8(vd: /ti set 12/07/2025)"
  clear: "&6/ti clear &7- Xoá thời hạn khỏi vật phẩm."
  reload: "&6/ti reload &7- Nạp lại plugin."
  help: "&6/ti help <page> &7- Xem hướng dẫn sử dụng."
```

---

## 🛠️ Hướng Dẫn Build Dự Án (Maven)

Yêu cầu máy cài đặt **Java 21** và **Maven**:

```bash
git clone https://github.com/SkyGamer/TimedItem.git
cd TimedItem
mvn clean package
```

File `.jar` sau khi build xong sẽ nằm tại thư mục `target/TimedItem-1.0.0.jar`.

---

## 📌 Yêu Cầu Kỹ Thuật
* **Phiên bản Server:** Spigot, Paper, Purpur 1.21 / 1.21.1+
* **Java:** Java 21+
* **Dependencies:** Độc lập hoàn toàn, không cần plugin phụ trợ (như ProtocolLib hay NBTAPI).
