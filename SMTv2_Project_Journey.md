# SMTv2 Project Journey

## 1. ภาพรวมระบบ

โปรเจกต์ **SMTv2 Manufacturing App** คือระบบ Android สำหรับช่วย Operator ในไลน์ SMT สแกนและจัดการ **Runcard** ระหว่างกระบวนการผลิต โดยเน้นให้ข้อมูลไหลอย่างถูกต้องตั้งแต่หน้าจอมือถือ ไปยัง API Backend และสุดท้ายลงฐานข้อมูล SQL Server

โครงสร้างหลักของระบบมี 3 ชั้น:

- **Android Frontend (Java):** รับข้อมูลจากผู้ใช้ เช่น User, Machine, Runcard, Good Qty, Scrap/Reject Qty และแสดงข้อมูล Production, Function Panels, Oper Tracking
- **Backend API (.NET / C#):** รับคำสั่งจาก Android ผ่าน REST API ตรวจสอบ business rules และสั่ง execute SQL ที่เกี่ยวข้อง
- **SQL Server Database:** เก็บข้อมูลจริงของ Runcard, transaction, history, hold, split, merge และ reject

การสื่อสารหลักใช้แนวคิด REST API โดยฝั่ง Android เรียก API ผ่าน Retrofit หรือ HTTP client แล้ว Backend จะเป็นคนจัดการ query และ transaction กับฐานข้อมูล เพื่อไม่ให้ Android เข้าถึง Database โดยตรง

## 2. 🗺️ Chronological Journey

### Phase 1: Foundation (การวางโครงสร้างเริ่มต้น)

- เริ่มจากหน้าจอสแกนพื้นฐานสำหรับกรอก **User**, **Machine**, และ **Runcard**
- เมื่อกด Verify ระบบจะดึงข้อมูล Current Production มาแสดง เช่น Description, Material, Work Order, Assy Lot, Qty และข้อมูล operation ปัจจุบัน
- สร้างตาราง **Oper Tracking** เพื่อให้ Operator เห็นลำดับงานและสถานะของแต่ละ operation ได้ในหน้าเดียว

### Phase 2: Logic & Validation (การสร้างกฎตรวจสอบข้อมูล)

- เพิ่ม Validation Gates ก่อนทำ action สำคัญ เพื่อป้องกันข้อมูลผิดไหลเข้าสู่ฐานข้อมูล
- กฎสำคัญประกอบด้วยการตรวจสอบ status, routing, activity และ B2B/block
- แนวคิดหลักคือให้ Backend เป็นศูนย์กลางของ business rules เพราะ Backend อยู่ใกล้ฐานข้อมูลและควบคุม transaction ได้ดีกว่า Android

### Phase 3: Inline Panels Refactoring (การเปลี่ยนจาก Floating Dialog เป็น Inline Panels)

- เดิมฟังก์ชันอย่าง Split, Hold, Merge และ Scrap/Reject เปิดเป็น floating dialog ทำให้ flow บนมือถือรู้สึกขาดตอน
- เราปรับเป็น **Inline Panels** ที่แสดงอยู่ในหน้าเดียวกับ Production Data เพื่อให้ Operator เห็นบริบทเดิมตลอดเวลา
- UI ใหม่ทำให้การทำงานต่อเนื่องขึ้น เช่น เปิด Split แล้วเห็น Mother Runcard, Qty, Remaining Qty และประวัติได้ในพื้นที่เดียวกัน

### Phase 4: Data Integrity & Save Confirm (การรักษาความถูกต้องของข้อมูล)

- เพิ่ม logic คำนวณ **Good Qty = Receive Qty - Scrap Qty** แบบ real-time
- ล็อก Good Qty โดย default และให้แก้ได้เฉพาะกรณี operation แรกหรือ receive qty ยังไม่มีข้อมูล
- ปรับ Oper Tracking ให้ scroll ได้ถูกต้องด้วยการแยก header/footer ออกจากพื้นที่ scroll
- ปรับ Summary row ให้เป็น static footer ไม่ใช่ปุ่ม เพื่อให้เหมือนระบบ MES ฝั่งเว็บ
- Wire ปุ่ม **SAVE CONFIRM** ให้เรียก API จริงเพื่อบันทึก production transaction และ refresh ข้อมูลหลัง save สำเร็จ

### Phase 5: UI/UX Optimization & Codebase Cleanup (การยกระดับหน้าจอและการทำความสะอาดโค้ด)

- **Sticky Mini-Header:** ยุบช่องสแกนขนาดใหญ่หลัง Verify สำเร็จให้กลายเป็น Header ขนาดเล็กที่ปักหมุดไว้ด้านบนสุด แสดงข้อมูล User, MC และ RC เพื่อคืนพื้นที่หน้าจอหลักให้ข้อมูลการผลิตที่สำคัญกว่า
- **Collapsible Oper Tracking:** เปลี่ยนตารางประวัติ Operation ให้เป็นแบบพับเก็บได้เหมือน Accordion พร้อมลูกศรเปิด/ปิด และซ่อนตารางอัตโนมัติเมื่อเปิดใช้งานฟังก์ชันอื่น เช่น Hold, Split, Merge หรือ Reject เพื่อให้หน้าจอไม่รก
- **Global Active Button State:** สร้างระบบจัดการสถานะสีของปุ่มฟังก์ชันแบบรวมศูนย์ เมื่อปุ่มใดถูกใช้งานจะเปลี่ยนเป็นสีน้ำเงินเข้มเพื่อบอกสถานะ Active และปุ่มอื่นจะกลับเป็นสีฟ้าอ่อน ทำให้ Operator รู้ทันทีว่ากำลังใช้งานฟังก์ชันอะไรอยู่
- **Codebase Cleanup:** ตรวจสอบ reference ก่อนลบไฟล์ทุกครั้ง และลบไฟล์โครงสร้างเก่าที่ไม่ได้ถูกเรียกใช้งานแล้ว เช่น placeholder manager เดิมและ API path เก่าที่ถูกแทนที่ด้วย flow ปัจจุบัน เพื่อลดความซับซ้อน ลดโอกาสสับสน และทำให้โค้ดดูแลต่อได้ง่ายขึ้น

#### UI/UX Polish รอบล่าสุด

- **Splash Screen & Branding:** เพิ่มหน้าจอโหลดแอป (Splash Screen) ด้วยโทนสีสว่างคลีนตา พร้อมแสดงโลโก้กลางหน้าจอประมาณ 1.5 - 2 วินาที เพื่อยกระดับความรู้สึกพรีเมียมและทำให้การเปิดแอปดูเป็นมืออาชีพมากขึ้น
- **Horizontal Function Menu:** จัดกลุ่มปุ่มฟังก์ชันทั้งหมดให้อยู่ในรูปแบบแถบเลื่อนแนวนอน (Horizontal Scroll) พร้อมข้อความกำกับ `Functions (Swipe left/right to explore)` เพื่อลดความแออัดในแนวตั้งและช่วยให้หน้าจอหลักเหลือพื้นที่สำหรับข้อมูลการผลิตมากขึ้น
- **Modern Typography:** ปรับฟอนต์หลักของแอปเป็น Sans-serif และใช้ระดับน้ำหนักฟอนต์ เช่น `sans-serif-medium` กับหัวข้อหรือค่าตัวเลขสำคัญ เพื่อให้หน้าจอดูสะอาด อ่านง่าย ลดความล้าสายตา และช่วยให้ Operator อ่านค่าปริมาณได้เร็วขึ้นระหว่างทำงานจริง
- **UI Decluttering & Standardization:** ลบแถบสถานะด้านบนแบบเดิมที่ซ้ำซ้อนออกเพื่อคืนพื้นที่หน้าจอ และปรับมาตรฐาน Inline Panel ทุกฟังก์ชันให้ปุ่ม **Close** อยู่มุมขวาบนของหัว panel เสมอ ทำให้ผู้ใช้เรียนรู้ตำแหน่งปิดหน้าต่างได้ครั้งเดียวและใช้งานต่อได้อย่างเป็นธรรมชาติ

## 3. ⚠️ Mistakes, Roadblocks & Lessons Learned

- **ปัญหา Floating Dialog ทำให้ UX ขาดตอน:** เมื่อ dialog ลอยทับหน้าจอ Operator จะเสียบริบทของ Production Data เดิม เราแก้ด้วย Inline Panels ที่อยู่ใน workflow เดียวกัน
- **ปัญหา Oper Tracking ถูกตัดขาด:** การจำกัดความสูงบน LinearLayout โดยตรงทำให้ content ถูกตัด แต่ไม่ scroll เราจึงย้ายข้อจำกัดความสูงไปไว้ที่ NestedScrollView และให้ LinearLayout ด้านในเป็น wrap_content
- **ปัญหา Good/Scrap เสี่ยงไม่ตรงกับ Receive:** ถ้าให้ผู้ใช้กรอกเองทั้งหมดอาจเกิดยอดรวมผิด เราจึงเพิ่มการคำนวณอัตโนมัติและ validation ก่อนส่ง API
- **ปัญหาโค้ดเก่าค้างในโปรเจกต์:** เมื่อ refactor เร็วมาก ไฟล์บางตัวอาจหมดหน้าที่แต่ยังอยู่ใน repo บทเรียนคือทุกการ cleanup ต้องใช้ reference scan และ build validation เสมอ

## 4. 🔄 End-to-End Data Flow

ตัวอย่างเมื่อผู้ใช้กรอก Scrap Qty แล้วกด **SAVE CONFIRM**:

1. Android อ่านค่าจากหน้าจอ เช่น Runcard, Work Center, Good Qty, Scrap Qty และ User ID
2. Input logic คำนวณ Good Qty จาก Receive Qty และ Scrap Qty พร้อมตรวจสอบว่ายอดรวมถูกต้อง
3. Android ส่งข้อมูลผ่าน API เช่น `/api/production/save`
4. Backend ตรวจสอบ request และ map ข้อมูลให้ตรงกับรูปแบบที่ SQL ต้องการ
5. Repository ฝั่ง Backend execute SQL เช่น `SaveProduction` เพื่อบันทึก transaction ลงตารางที่เกี่ยวข้อง เช่น `RC_Transection`
6. เมื่อ Backend ตอบ success กลับมา Android จะแสดงข้อความสำเร็จ ล้าง input และ refresh Runcard เพื่อให้หน้าจอเลื่อนไปยัง operation ถัดไปอย่างถูกต้อง

## 5. หลักคิดสำหรับการพัฒนาต่อ

- ให้ Android ดูแล UX และ input validation เบื้องต้น
- ให้ Backend ดูแล business rules, validation gates และ database transaction
- อย่าให้ UI state กระจัดกระจาย ควรมี manager หรือ method กลางสำหรับ state สำคัญ เช่น active function button และ inline panel visibility
- ทุกครั้งที่ลบไฟล์ ต้องตอบให้ได้ว่าไฟล์นั้นไม่มี reference แล้วจริง และต้อง build ผ่านหลังลบ
