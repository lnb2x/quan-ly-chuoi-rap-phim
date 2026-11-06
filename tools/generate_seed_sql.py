# -*- coding: utf-8 -*-
"""
Deterministic generator for database/seed/10_seed.sql
Produces:
  - 4 cinema branches
  - 12 screening rooms (3 per branch)
  - 3 seat types
  - 960 physical seats (80 per room)
  - 8 genres
  - 20 movies + 42 movie_genre rows
  - 12 employees
  - 45 customers
  - 88 screenings (2026-11-04 .. 2026-11-10)
  - 160 bookings, ~340 tickets, ~180 payments covering:
      * CONFIRMED + SUCCESS (including 15 bookings with an earlier FAILED payment attempt + 1 SUCCESS payment)
      * PENDING + PENDING
      * CANCELLED + FAILED (with seats resold to subsequent CONFIRMED bookings)
      * CANCELLED + REFUNDED
"""
from datetime import datetime, timedelta
from pathlib import Path

OUT_PATH = Path(__file__).resolve().parent.parent / "database" / "seed" / "10_seed.sql"


def sql_str(val):
    if val is None:
        return "NULL"
    escaped = str(val).replace("\\", "\\\\").replace("'", "''")
    return f"'{escaped}'"


def generate():
    lines = []
    lines.append("-- =============================================================================")
    lines.append("-- TỆP: 10_seed.sql")
    lines.append("-- MỤC ĐÍCH: Dữ liệu mẫu tất định (Reproducible Seed Data) cho Hệ thống Quản lý")
    lines.append("--           Chuỗi Rạp Chiếu Phim:")
    lines.append("--           4 chi nhánh, 12 phòng chiếu, 960 ghế, 8 thể loại, 20 bộ phim,")
    lines.append("--           12 nhân viên, 45 khách hàng, 88 suất chiếu, 160 đơn đặt vé.")
    lines.append("-- =============================================================================")
    lines.append("")
    lines.append("SET NAMES utf8mb4;")
    lines.append("SET FOREIGN_KEY_CHECKS = 0;")
    lines.append("TRUNCATE TABLE payment;")
    lines.append("TRUNCATE TABLE ticket;")
    lines.append("TRUNCATE TABLE booking;")
    lines.append("TRUNCATE TABLE screening;")
    lines.append("TRUNCATE TABLE movie_genre;")
    lines.append("TRUNCATE TABLE employee;")
    lines.append("TRUNCATE TABLE customer;")
    lines.append("TRUNCATE TABLE seat;")
    lines.append("TRUNCATE TABLE seat_type;")
    lines.append("TRUNCATE TABLE screening_room;")
    lines.append("TRUNCATE TABLE genre;")
    lines.append("TRUNCATE TABLE movie;")
    lines.append("TRUNCATE TABLE cinema_branch;")
    lines.append("SET FOREIGN_KEY_CHECKS = 1;")
    lines.append("")

    # 1. Branches (4)
    branches = [
        (1, "CN-HN01", "CineViet Vincom Bà Triệu", "Tầng 6, 191 Bà Triệu, Quận Hai Bà Trưng, Hà Nội", "Hà Nội", "02439748888", 1),
        (2, "CN-HCM01", "CineViet Landmark 81", "Tầng B1, 720A Điện Biên Phủ, Quận Bình Thạnh, TP. Hồ Chí Minh", "TP. Hồ Chí Minh", "02836209999", 1),
        (3, "CN-DN01", "CineViet Cầu Rồng Đà Nẵng", "Tầng 4, 910A Ngô Quyền, Quận Sơn Trà, Đà Nẵng", "Đà Nẵng", "02363886666", 1),
        (4, "CN-HP01", "CineViet Aeon Mall Hải Phòng", "Tầng 3, Số 10 Võ Nguyên Giáp, Quận Lê Chân, Hải Phòng", "Hải Phòng", "02253667777", 1),
    ]
    lines.append("-- 1. Chi nhánh rạp (4 chi nhánh)")
    lines.append("INSERT INTO cinema_branch (branch_id, branch_code, branch_name, address, city, phone, active) VALUES")
    lines.append(",\n".join(
        f"({b[0]}, {sql_str(b[1])}, {sql_str(b[2])}, {sql_str(b[3])}, {sql_str(b[4])}, {sql_str(b[5])}, {b[6]})"
        for b in branches
    ) + ";\n")

    # 2. Rooms (12 rooms: 3 per branch)
    rooms = []
    room_specs = [
        ("Phòng 01 - Standard 2D", "2D"),
        ("Phòng 02 - Dolby 3D", "3D"),
        ("Phòng 03 - Laser IMAX", "IMAX"),
    ]
    room_id = 1
    for b in branches:
        for rname, rtype in room_specs:
            rooms.append((room_id, b[0], rname, rtype, 1))
            room_id += 1

    lines.append("-- 2. Phòng chiếu (12 phòng chiếu)")
    lines.append("INSERT INTO screening_room (room_id, branch_id, room_name, room_type, active) VALUES")
    lines.append(",\n".join(
        f"({r[0]}, {r[1]}, {sql_str(r[2])}, {sql_str(r[3])}, {r[4]})"
        for r in rooms
    ) + ";\n")

    # 3. Seat types (3)
    seat_types = [
        ("STD", "Ghế Thường (Standard)", "0.00"),
        ("VIP", "Ghế VIP Trung tâm", "20000.00"),
        ("COUPLE", "Ghế Đôi Sweetbox", "45000.00"),
    ]
    lines.append("-- 3. Hạng ghế (3 hạng ghế)")
    lines.append("INSERT INTO seat_type (seat_type_code, type_name, surcharge) VALUES")
    lines.append(",\n".join(
        f"({sql_str(st[0])}, {sql_str(st[1])}, {st[2]})"
        for st in seat_types
    ) + ";\n")

    # 4. Physical seats (12 rooms x 80 seats = 960 seats)
    # Rows A, B, C, D -> STD; E, F, G -> VIP; H -> COUPLE (10 seats per row)
    seats = []
    room_seats_map = {}
    seat_id = 1
    row_types = [
        ("A", "STD", 0),
        ("B", "STD", 0),
        ("C", "STD", 0),
        ("D", "STD", 0),
        ("E", "VIP", 20000),
        ("F", "VIP", 20000),
        ("G", "VIP", 20000),
        ("H", "COUPLE", 45000),
    ]
    for r in rooms:
        r_id = r[0]
        room_seats_map[r_id] = []
        for s_row, st_code, surcharge in row_types:
            for s_num in range(1, 11):
                seats.append((seat_id, r_id, s_row, s_num, st_code, surcharge))
                room_seats_map[r_id].append((seat_id, s_row, s_num, st_code, surcharge))
                seat_id += 1

    lines.append("-- 4. Ghế vật lý (960 ghế thuộc 12 phòng chiếu)")
    for chunk_start in range(0, len(seats), 240):
        chunk = seats[chunk_start:chunk_start + 240]
        lines.append("INSERT INTO seat (seat_id, room_id, seat_row, seat_number, seat_type_code) VALUES")
        lines.append(",\n".join(
            f"({s[0]}, {s[1]}, {sql_str(s[2])}, {s[3]}, {sql_str(s[4])})"
            for s in chunk
        ) + ";\n")

    # 5. Genres (8)
    genres = [
        (1, "Hành động", "Phim hành động kịch tính, võ thuật, rượt đuổi tốc độ cao"),
        (2, "Tâm lý - Tình cảm", "Phim khai thác chiều sâu nội tâm, gia đình và tình yêu"),
        (3, "Khoa học viễn tưởng", "Phim khám phá vũ trụ, công nghệ tương lai và du hành không gian"),
        (4, "Hoạt hình - Gia đình", "Phim hoạt hình dành cho thiếu nhi và gia đình"),
        (5, "Kinh dị - Bí ẩn", "Phim giật gân, trinh thám và yếu tố siêu nhiên"),
        (6, "Hài hước", "Phim giải trí mang tiếng cười sảng khoái"),
        (7, "Lịch sử - Chiến tranh", "Phim tái hiện các sự kiện lịch sử hào hùng"),
        (8, "Phiêu lưu - Giả tưởng", "Phim hành trình khám phá thế giới kỳ ảo"),
    ]
    lines.append("-- 5. Thể loại phim (8 thể loại)")
    lines.append("INSERT INTO genre (genre_id, genre_name, description) VALUES")
    lines.append(",\n".join(
        f"({g[0]}, {sql_str(g[1])}, {sql_str(g[2])})"
        for g in genres
    ) + ";\n")

    # 6. Movies (20)
    movies = [
        (1, "MV-001", "Đào, Phở và Piano", 105, "2026-09-01", "T13", "Việt Nam", "Bản hùng ca lãng mạn và bi tráng của người dân Hà Nội mùa đông năm 1946."),
        (2, "MV-002", "Mai", 131, "2026-09-10", "T18", "Việt Nam", "Câu chuyện tâm lý sâu sắc về số phận và khát vọng hạnh phúc của người phụ nữ."),
        (3, "MV-003", "Lật Mặt 7: Một Điều Ước", 138, "2026-09-15", "K", "Việt Nam", "Hành trình gắn kết tình mẫu tử ấm áp qua những lát cắt cuộc sống của 5 người con."),
        (4, "MV-004", "Mắt Biếc", 117, "2026-09-20", "T13", "Việt Nam", "Mối tình đơn phương trong trẻo của Ngạn dành cho Hà Lan nơi làng Đo Đo."),
        (5, "MV-005", "Dune: Hành Tinh Cát - Phần Hai", 165, "2026-09-25", "T13", "Mỹ", "Cuộc chiến vĩ đại của Paul Atreides cùng tộc người Fremen trên hành tinh Arrakis."),
        (6, "MV-006", "Oppenheimer", 160, "2026-10-01", "T16", "Mỹ", "Chân dung nhà vật lý lý thuyết J. Robert Oppenheimer và dự án Manhattan lịch sử."),
        (7, "MV-007", "Kẻ Đánh Cắp Giấc Mơ (Inception)", 148, "2026-10-05", "T13", "Mỹ", "Phi vụ xâm nhập tầng sâu tiềm thức thông qua những giấc mơ lồng trong mơ."),
        (8, "MV-008", "Hố Đen Tử Thần (Interstellar)", 162, "2026-10-08", "T13", "Mỹ", "Chuyến du hành xuyên hố sâu vũ trụ tìm kiếm hành tinh sinh sống mới cho nhân loại."),
        (9, "MV-009", "Những Mảnh Ghép Cảm Xúc 2", 96, "2026-10-10", "P", "Mỹ", "Thế giới cảm xúc tuổi dậy thì đầy màu sắc trong tâm trí cô bé Riley."),
        (10, "MV-010", "Vùng Đất Linh Hồn (Spirited Away)", 125, "2026-10-12", "P", "Nhật Bản", "Cuộc phiêu lưu kỳ diệu của cô bé Chihiro trong thế giới linh hồn huyền bí."),
        (11, "MV-011", "Ký Sinh Trùng (Parasite)", 132, "2026-10-14", "T18", "Hàn Quốc", "Tác phẩm châm biếm sắc sảo về khoảng cách giàu nghèo giữa hai gia đình."),
        (12, "MV-012", "Quật Mộ Trùng Ma (Exhuma)", 134, "2026-10-16", "T16", "Hàn Quốc", "Bí ẩn tâm linh rùng rợn đằng sau ngôi mộ cổ trên đỉnh núi hẻo lánh."),
        (13, "MV-013", "Nhà Bà Nữ", 102, "2026-10-18", "T16", "Việt Nam", "Mâu thuẫn thế hệ giữa người mẹ độc đoán và cô con gái út trong gia đình bán bánh canh."),
        (14, "MV-014", "Bố Già", 128, "2026-10-20", "T13", "Việt Nam", "Tình phụ tử cảm động nơi xóm lao động nghèo giữa lòng Sài Gòn."),
        (15, "MV-015", " Đất Rừng Phương Nam", 110, "2026-10-22", "K", "Việt Nam", "Hành trình tìm cha đầy nghĩa khí của cậu bé An khắp vùng sông nước miền Tây."),
        (16, "MV-016", "Thám Tử Lừng Danh Conan: Ngôi Sao 5 Cánh", 111, "2026-10-24", "K", "Nhật Bản", "Cuộc đối đầu trí tuệ giữa Conan, Heiji và siêu đạo chích Kaito Kid tại Hakodate."),
        (17, "MV-017", "Vệ Binh Dải Ngân Hà 3", 150, "2026-10-26", "T13", "Mỹ", "Chuyến phiêu lưu cuối cùng đầy cảm xúc để bảo vệ người bạn Rocket."),
        (18, "MV-018", "Người Nhện: Du Hành Vũ Trụ Nhện", 140, "2026-10-28", "P", "Mỹ", "Miles Morales bước vào đa vũ trụ cùng hàng trăm phiên bản Người Nhện khác nhau."),
        (19, "MV-019", "Kẻ Trộm Mặt Trăng 4", 95, "2026-10-30", "P", "Mỹ", "Gia đình Gru và biệt đội Minions tinh nghịch đối mặt kẻ thù mới."),
        (20, "MV-020", "Địa Đạo: Mặt Trời Trong Bóng Tối", 124, "2026-11-01", "T16", "Việt Nam", "Cuộc chiến đấu kiên cường dưới lòng địa đạo Củ Chi lịch sử."),
    ]
    lines.append("-- 6. Phim điện ảnh (20 bộ phim)")
    lines.append("INSERT INTO movie (movie_id, movie_code, title, duration_minutes, release_date, age_rating, country, description) VALUES")
    lines.append(",\n".join(
        f"({m[0]}, {sql_str(m[1])}, {sql_str(m[2])}, {m[3]}, {sql_str(m[4])}, {sql_str(m[5])}, {sql_str(m[6])}, {sql_str(m[7])})"
        for m in movies
    ) + ";\n")

    # 7. Movie-Genre (42 links)
    movie_genres = [
        (1, 7), (1, 2),
        (2, 2), (2, 6),
        (3, 2), (3, 6),
        (4, 2),
        (5, 3), (5, 1), (5, 8),
        (6, 7), (6, 2),
        (7, 3), (7, 1),
        (8, 3), (8, 8),
        (9, 4), (9, 6),
        (10, 4), (10, 8),
        (11, 2), (11, 5),
        (12, 5),
        (13, 2), (13, 6),
        (14, 2), (14, 6),
        (15, 8), (15, 7), (15, 2),
        (16, 4), (16, 5), (16, 1),
        (17, 3), (17, 1), (17, 6),
        (18, 4), (18, 1), (18, 3),
        (19, 4), (19, 6),
        (20, 7), (20, 1),
    ]
    lines.append("-- 7. Phân loại Phim - Thể loại (43 dòng)")
    lines.append("INSERT INTO movie_genre (movie_id, genre_id) VALUES")
    lines.append(",\n".join(f"({mg[0]}, {mg[1]})" for mg in movie_genres) + ";\n")

    # 8. Employees (12 employees: 1 Manager, 1 Accountant, 1 Cashier per branch)
    employees = [
        (1, 1, "NV-HN01", "Nguyễn Minh Hoàng", "0901100101", "MANAGER", "2023-02-15"),
        (2, 1, "NV-HN02", "Trần Thị Thu Hà", "0901100102", "ACCOUNTANT", "2023-03-10"),
        (3, 1, "NV-HN03", "Lê Quang Huy", "0901100103", "CASHIER", "2024-01-20"),
        (4, 2, "NV-HCM01", "Phạm Gia Bảo", "0902200201", "MANAGER", "2023-01-12"),
        (5, 2, "NV-HCM02", "Võ Ngọc Lan Anh", "0902200202", "ACCOUNTANT", "2023-05-18"),
        (6, 2, "NV-HCM03", "Đặng Quốc Khánh", "0902200203", "CASHIER", "2024-02-11"),
        (7, 3, "NV-DN01", "Hoàng Văn Phúc", "0903300301", "MANAGER", "2023-04-01"),
        (8, 3, "NV-DN02", "Bùi Thị Thanh Tâm", "0903300302", "ACCOUNTANT", "2023-06-22"),
        (9, 3, "NV-DN03", "Ngô Tuấn Kiệt", "0903300303", "CASHIER", "2024-03-15"),
        (10, 4, "NV-HP01", "Vũ Đức Thịnh", "0904400401", "MANAGER", "2023-07-09"),
        (11, 4, "NV-HP02", "Đỗ Mai Phương", "0904400402", "ACCOUNTANT", "2023-08-25"),
        (12, 4, "NV-HP03", "Phan Thanh Tùng", "0904400403", "CASHIER", "2024-04-05"),
    ]
    lines.append("-- 8. Nhân viên (12 nhân sự tại 4 chi nhánh)")
    lines.append("INSERT INTO employee (employee_id, branch_id, employee_code, full_name, phone, role, hired_date) VALUES")
    lines.append(",\n".join(
        f"({e[0]}, {e[1]}, {sql_str(e[2])}, {sql_str(e[3])}, {sql_str(e[4])}, {sql_str(e[5])}, {sql_str(e[6])})"
        for e in employees
    ) + ";\n")

    # 9. Customers (45 customers)
    surnames = ["Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương"]
    middles = ["Văn", "Thị", "Minh", "Quốc", "Thanh", "Ngọc", "Gia", "Đức", "Hoài", "Bảo", "Phương", "Tuấn", "Hải", "Thu", "Khánh"]
    givens = ["An", "Bình", "Châu", "Dũng", "Đạt", "Giang", "Hà", "Hải", "Hạnh", "Hiếu", "Hùng", "Hương", "Khoa", "Linh", "Long",
              "Mai", "Nam", "Nga", "Nhung", "Oanh", "Phúc", "Phương", "Quân", "Quỳnh", "Sơn", "Thảo", "Thịnh", "Trang", "Trinh", "Trung",
              "Tú", "Tuấn", "Tùng", "Uyên", "Vân", "Việt", "Vinh", "Vy", "Yến", "Lâm", "Khôi", "Kiên", "Thắng", "Lộc", "Tâm"]
    customers = []
    for cid in range(1, 46):
        fname = f"{surnames[(cid - 1) % len(surnames)]} {middles[(cid * 3) % len(middles)]} {givens[cid - 1]}"
        phone = f"0918{cid:06d}"
        email = f"khachhang{cid:02d}@cineviet.vn"
        bdate = f"{1988 + (cid % 18):04d}-{((cid % 12) + 1):02d}-{((cid % 27) + 1):02d}"
        cat = f"2026-10-{((cid % 28) + 1):02d} 09:30:00"
        customers.append((cid, fname, phone, email, bdate, cat))

    lines.append("-- 9. Khách hàng thành viên (45 khách hàng)")
    lines.append("INSERT INTO customer (customer_id, full_name, phone, email, birth_date, created_at) VALUES")
    lines.append(",\n".join(
        f"({c[0]}, {sql_str(c[1])}, {sql_str(c[2])}, {sql_str(c[3])}, {sql_str(c[4])}, {sql_str(c[5])})"
        for c in customers
    ) + ";\n")

    # 10. Screenings (88 screenings across 12 rooms over dates 2026-11-04 .. 2026-11-10)
    # Time slots chosen with >= 3.5 hours spacing so even 165-min movies + 20-min cleaning never overlap:
    # Slot 0: 09:00, Slot 1: 13:00, Slot 2: 17:00, Slot 3: 20:30
    movie_dur_map = {m[0]: m[3] for m in movies}
    room_type_map = {r[0]: r[3] for r in rooms}
    room_branch_map = {r[0]: r[1] for r in rooms}

    base_date = datetime(2026, 11, 4)
    slot_hours = [(9, 0), (13, 0), (17, 0), (20, 30)]
    screenings = []
    scr_id = 1
    for day_offset in range(7):  # 7 days: Nov 4 .. Nov 10, 2026
        cur_date = base_date + timedelta(days=day_offset)
        for r in rooms:
            r_id = r[0]
            r_type = r[3]
            # Pick 1 or 2 slots per room per day so total = 88 screenings
            slots_for_room = [0, 2] if (r_id + day_offset) % 2 == 0 else [1, 3]
            if day_offset == 6 and r_id > 4:
                slots_for_room = [1]  # keeps total screenings = 88
            for s_idx in slots_for_room:
                m_id = ((scr_id * 3 + r_id + day_offset) % 20) + 1
                dur = movie_dur_map[m_id]
                sh, sm = slot_hours[s_idx]
                st_dt = cur_date.replace(hour=sh, minute=sm, second=0)
                end_dt = st_dt + timedelta(minutes=dur + 20)
                if r_type == "2D":
                    base_price = "85000.00" if s_idx < 2 else "95000.00"
                elif r_type == "3D":
                    base_price = "110000.00" if s_idx < 2 else "125000.00"
                else:
                    base_price = "145000.00" if s_idx < 2 else "160000.00"
                status = "CANCELLED" if scr_id in (85, 86, 87, 88) else "OPEN"
                screenings.append((
                    scr_id,
                    m_id,
                    r_id,
                    st_dt.strftime("%Y-%m-%d %H:%M:%S"),
                    end_dt.strftime("%Y-%m-%d %H:%M:%S"),
                    base_price,
                    status,
                ))
                scr_id += 1

    lines.append(f"-- 10. Suất chiếu ({len(screenings)} suất chiếu không chồng lấn lịch)")
    lines.append("INSERT INTO screening (screening_id, movie_id, room_id, start_time, end_time, base_price, status) VALUES")
    lines.append(",\n".join(
        f"({s[0]}, {s[1]}, {s[2]}, {sql_str(s[3])}, {sql_str(s[4])}, {s[5]}, {sql_str(s[6])})"
        for s in screenings
    ) + ";\n")

    # 11. Bookings (160), Tickets, Payments
    # Cashier per branch: branch 1 -> emp 3, branch 2 -> emp 6, branch 3 -> emp 9, branch 4 -> emp 12
    cashier_by_branch = {1: 3, 2: 6, 3: 9, 4: 12}
    open_screenings = [s for s in screenings if s[6] == "OPEN"]  # 84 open screenings

    # Track next free seat index per screening
    next_seat_idx = {s[0]: 0 for s in open_screenings}

    bookings = []
    tickets = []
    payments = []
    ticket_id = 1
    payment_id = 1

    methods = ["CASH", "CARD", "TRANSFER", "EWALLET"]

    # Booking IDs 1..10: CANCELLED unpaid bookings (seats will be resold in bookings 11..20!)
    # Booking IDs 11..135: 125 CONFIRMED bookings with SUCCESS payment (15 of them also have an earlier FAILED payment)
    # Booking IDs 136..150: 15 PENDING bookings with PENDING payment
    # Booking IDs 151..160: 10 CANCELLED bookings that were paid and REFUNDED
    resell_seats_by_booking = {}

    for b_id in range(1, 161):
        b_code = f"BK-202611-{b_id:04d}"
        if 1 <= b_id <= 10:
            scr = open_screenings[b_id - 1]
            b_status = "CANCELLED"
            t_status = "CANCELLED"
            p_status = "FAILED"
        elif 11 <= b_id <= 20:
            # Reuse the exact same screening and seats that were cancelled in bookings 1..10 to prove seat resale!
            scr = open_screenings[b_id - 11]
            b_status = "CONFIRMED"
            t_status = "VALID"
            p_status = "SUCCESS"
        elif 21 <= b_id <= 135:
            scr = open_screenings[(b_id - 1) % len(open_screenings)]
            b_status = "CONFIRMED"
            t_status = "VALID"
            p_status = "SUCCESS"
        elif 136 <= b_id <= 150:
            scr = open_screenings[(b_id - 1) % len(open_screenings)]
            b_status = "PENDING"
            t_status = "VALID"
            p_status = "PENDING"
        else:
            scr = open_screenings[(b_id - 1) % len(open_screenings)]
            b_status = "CANCELLED"
            t_status = "CANCELLED"
            p_status = "REFUNDED"

        s_id = scr[0]
        r_id = scr[2]
        base_price_num = int(float(scr[5]))
        branch_id = room_branch_map[r_id]
        emp_id = cashier_by_branch[branch_id]

        # Every 5th booking is a walk-in customer (customer_id = NULL)
        cust_id = None if (b_id % 5 == 0) else ((b_id % 45) + 1)

        scr_start = datetime.strptime(scr[3], "%Y-%m-%d %H:%M:%S")
        booked_dt = scr_start - timedelta(hours=4, minutes=(b_id % 45))
        booked_str = booked_dt.strftime("%Y-%m-%d %H:%M:%S")

        bookings.append((b_id, b_code, s_id, cust_id, emp_id, booked_str, b_status))

        # Select 2 or 3 seats for this booking
        num_tickets = 2 if (b_id % 3 != 0) else 3
        room_seat_list = room_seats_map[r_id]

        if 11 <= b_id <= 20:
            chosen_seats = resell_seats_by_booking[b_id - 10]
        else:
            start_idx = next_seat_idx[s_id]
            chosen_seats = room_seat_list[start_idx:start_idx + num_tickets]
            next_seat_idx[s_id] = start_idx + num_tickets
            if 1 <= b_id <= 10:
                resell_seats_by_booking[b_id] = chosen_seats

        booking_total = 0
        cancel_str = (booked_dt + timedelta(minutes=25)).strftime("%Y-%m-%d %H:%M:%S") if t_status == "CANCELLED" else None
        for seat_info in chosen_seats:
            seat_pk, _, _, _, surcharge_num = seat_info
            u_price = base_price_num + surcharge_num
            booking_total += u_price
            tickets.append((
                ticket_id,
                b_id,
                s_id,
                seat_pk,
                f"{u_price:.2f}",
                t_status,
                cancel_str,
            ))
            ticket_id += 1

        method = methods[b_id % len(methods)]
        paid_str = (booked_dt + timedelta(minutes=3)).strftime("%Y-%m-%d %H:%M:%S")

        # For bookings 21..35 (15 confirmed bookings), add an initial FAILED card attempt before the SUCCESS payment
        if 21 <= b_id <= 35:
            fail_str = (booked_dt + timedelta(minutes=1)).strftime("%Y-%m-%d %H:%M:%S")
            payments.append((
                payment_id,
                b_id,
                f"{booking_total:.2f}",
                "CARD",
                "FAILED",
                fail_str,
                f"TXN-FAIL-{b_id:04d}",
            ))
            payment_id += 1

        payments.append((
            payment_id,
            b_id,
            f"{booking_total:.2f}",
            method,
            p_status,
            paid_str if p_status in ("SUCCESS", "REFUNDED") else None,
            f"TXN-{p_status[:3]}-{b_id:04d}",
        ))
        payment_id += 1

    lines.append(f"-- 11. Đơn đặt vé ({len(bookings)} đơn đặt vé)")
    lines.append("INSERT INTO booking (booking_id, booking_code, screening_id, customer_id, employee_id, booked_at, booking_status) VALUES")
    lines.append(",\n".join(
        f"({b[0]}, {sql_str(b[1])}, {b[2]}, {sql_str(b[3])}, {b[4]}, {sql_str(b[5])}, {sql_str(b[6])})"
        for b in bookings
    ) + ";\n")

    lines.append(f"-- 12. Vé xem phim ({len(tickets)} vé, bao gồm cả ghế hủy và bán lại)")
    lines.append("INSERT INTO ticket (ticket_id, booking_id, screening_id, seat_id, unit_price, ticket_status, cancelled_at) VALUES")
    lines.append(",\n".join(
        f"({t[0]}, {t[1]}, {t[2]}, {t[3]}, {t[4]}, {sql_str(t[5])}, {sql_str(t[6])})"
        for t in tickets
    ) + ";\n")

    lines.append(f"-- 13. Giao dịch thanh toán ({len(payments)} giao dịch)")
    lines.append("INSERT INTO payment (payment_id, booking_id, amount, payment_method, payment_status, paid_at, transaction_ref) VALUES")
    lines.append(",\n".join(
        f"({p[0]}, {p[1]}, {p[2]}, {sql_str(p[3])}, {sql_str(p[4])}, {sql_str(p[5])}, {sql_str(p[6])})"
        for p in payments
    ) + ";\n")

    OUT_PATH.write_text("\n".join(lines), encoding="utf-8")
    print(f"Generated {OUT_PATH}: branches={len(branches)}, rooms={len(rooms)}, seats={len(seats)}, "
          f"movies={len(movies)}, screenings={len(screenings)}, bookings={len(bookings)}, "
          f"tickets={len(tickets)}, payments={len(payments)}")


if __name__ == "__main__":
    generate()
