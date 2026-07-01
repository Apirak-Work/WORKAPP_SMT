# 🚀 SMTv2 Smart Scanning App - Project Documentation
> **Last Updated:** 15 June 2026

## 📌 1. Project Overview (ภาพรวมของโปรเจกต์)
**Target Environment:** STARS Microelectronics (Thailand) Public Co., Ltd. (Production Line)
**Core Objective:** พัฒนาแอปพลิเคชัน Android สำหรับสแกนบาร์โค้ดในไลน์การผลิต (Smart Scanning App) เพื่อใช้งานบนเครื่องสแกนเนอร์แฮนด์เฮลด์ (เช่น Zebra)
**Key Focus:** เน้นการออกแบบที่ผู้ใช้งาน (Operator) ทำงานได้รวดเร็ว ลดข้อผิดพลาด (Data-Centric) หน้าจอสะอาดตา (Ergonomic UI) และมีระบบป้องกันความผิดพลาดจากการทำงานหน้างานจริง (Foolproof Mechanisms)

## 🛠️ 2. Tech Stack & Architecture (เทคโนโลยีที่ใช้)
* **Frontend (Mobile App):** Android Native (Java) + XML สำหรับออกแบบ UI
* **Backend (API):** .NET 7 (C#) Web API วิ่งผ่านพอร์ต `5000`
* **Database:** SQL Server (รอเชื่อมโยงโครงสร้าง Schema)
* **Networking:** ใช้ไลบรารี `Retrofit` สำหรับยิง API สื่อสารระหว่าง Android และ .NET Backend

---

## 🟢 3. Current Progress & Core Mechanics (สถานะปัจจุบันและหลักการทำงาน)
*อัปเดตล่าสุด: ปรับแต่ง UI/UX และวางระบบป้องกันข้อผิดพลาด (Safeguards) เสร็จสมบูรณ์*

### 3.1 การจัดลำดับหน้าจอและการแสดงผล (UI/UX & Workflow)
* **Splash Screen & Versioning:** หน้าโหลดแอปเริ่มต้นดีไซน์คลีน (สีขาว/ฟ้า `#F4F9FD`) มีโลโก้ **"starone Smart"** พร้อมระบบดึงเลขเวอร์ชันแอปอัตโนมัติ (`BuildConfig.VERSION_NAME`) มาโชว์ด้านล่าง เพื่อให้ทีม IT ตรวจสอบเวอร์ชันตอนทำ UAT ได้ง่าย
* **Sequential Scan Logic:** ระบบบังคับสแกนตามลำดับ (User -> Machine -> Runcard) เพื่อป้องกันการข้ามขั้นตอน
* **Dynamic UI (การซ่อน/โชว์ UI อัจฉริยะ):** เมื่อสแกนและกดยืนยัน (Verify) ผ่าน โลโก้ด้านล่างจะถูกซ่อน (Gone) อัตโนมัติ เพื่อคืนพื้นที่หน้าจอให้ตารางข้อมูลการผลิต (Production Data)
* **Compact Oper Tracking:** บีบอัดขนาดตารางประวัติการทำงาน (Oper Tracking) ให้เล็กลง (ลด Padding และ Text Size) เพื่อไม่ให้ดันปุ่มกดหลักตกขอบจอ

### 3.2 ระบบการตอบสนองหน้างาน (Tactile & Feedback System)
* **Haptic & Sound Engine:** ใช้ระบบสั่น (Vibrator) และเสียง (ToneGenerator)
    * *สแกนผ่าน:* สั่นสั้นๆ 1 ครั้ง (200ms)
    * *เกิด Error:* สั่นเตือน 2 ครั้งติดกัน พร้อมเสียง Beep แจ้งเตือนพนักงานทันที
* **Auto-Hide Soft Keyboard:** เขียนโค้ดดักจับ `dispatchTouchEvent` ระดับลึก หากพนักงานแตะพื้นที่ว่างบนหน้าจอ คีย์บอร์ดจะถูกซ่อนอัตโนมัติ เพื่อแก้ปัญหาคีย์บอร์ดบังปุ่มกดหรือตารางข้อมูล
* **On-Screen Error Display:** เพิ่มกล่องข้อความสีแดงใต้ปุ่ม Verify เพื่อโชว์ Error Message จาก Backend (เช่น Network Timeout) ช่วยให้จับบั๊กหน้างานได้ทันทีโดยไม่ต้องต่อคอมพิวเตอร์ดู Log

### 3.3 ระบบป้องกันความผิดพลาด (Safeguards & Data Protection)
* **Anti-Spam Button (ป้องกันการเบิ้ลข้อมูล):** เมื่อกดปุ่ม "SAVE CONFIRM" ปุ่มจะถูกล็อค (Disable) และโชว์สถานะ "กำลังโหลด..." ทันที เพื่อป้องกันพนักงานกดเซฟซ้ำซ้อนจนข้อมูลลง Database ซ้ำกัน
* **Smart "Return to Scan" Button (ปุ่มล้างข้อมูลอัจฉริยะ):**
    * จัดวางปุ่มไว้ข้างๆ (Side-by-side) กับปุ่ม SAVE CONFIRM เพื่อประหยัดพื้นที่แนวตั้ง
    * มีระบบ **2-Tier State Observer (`hasUnsavedChanges`)**:
        * **Tier 1 (Warning):** มีระบบติดตามการเปลี่ยนแปลงของช่องกรอกข้อมูล หากพนักงานพิมพ์ค่ายอด Scrap ค้างไว้แล้วกดปุ่มเคลียร์ ระบบจะเด้งหน้าต่างเตือนสีแดงว่า *"คุณมีข้อมูลที่ยังไม่ได้บันทึก ยืนยันที่จะล้างทิ้งหรือไม่?"* เพื่อป้องกันข้อมูลสูญหาย
        * **Tier 2 (Safe):** หากยังไม่ได้พิมพ์อะไร หรือเพิ่งกดเซฟเสร็จ ระบบจะถามยืนยันปกติเพื่อกลับสู่หน้าสแกนบาร์โค้ดใบใหม่

---

## 🚧 4. Next Steps / Pending (สิ่งที่ต้องพัฒนาระยะต่อไป)
1. **Core API & Database Integration:** รอโครงสร้างตาราง (Table Columns) ที่สมบูรณ์จากฐานข้อมูล SQL Server เพื่อนำมาสร้าง Data Classes ฝั่ง Android ให้ตรงกัน
2. **Advanced Functions:** ตรวจสอบและเชื่อม API จริงให้ครบถ้วนกับปุ่มฟังก์ชันพิเศษบนหน้าจอ ได้แก่ `Hold` (ระงับชั่วคราว), `Split` (แบ่งล็อต), `Merge` (รวมล็อต) และ `Reject` (งานเสีย)
3. **Production Deployment:** เตรียมปรับ Base URL จาก Local IP (`192.168.x.x`) ให้ชี้ไปยัง Production Server ของโรงงาน

> **Technical Note:** Backend ปัจจุบันใช้ .NET 7 ซึ่งเป็น runtime ที่หมดระยะ support แล้ว ควรวางแผนอัปเกรดเป็น .NET LTS สำหรับการใช้งานระยะยาวใน Production
