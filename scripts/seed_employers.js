const fs = require('fs');
const path = require('path');
const xlsx = require('xlsx');
const axios = require('axios');

// ==========================================
// الإعدادات والتكوين
// ==========================================
const DIRECTORY_PATH = path.resolve('..', 'Unified_Benefit_Tables_Ready_V3');
const API_BASE_URL = 'http://localhost:8080/api/v1';

// ضع بيانات الدخول الخاصة بالـ Super Admin هنا
const ADMIN_CREDENTIALS = {
  email: 'admin@waad.tba', // أو username
  password: 'password'
};

// ==========================================
// دالة استخراج الرقم من النص (مثلاً: سقف عام 50,000)
// ==========================================
function extractCeiling(text) {
  if (typeof text !== 'string') return null;
  const match = text.match(/\d+([,.]\d+)?/);
  if (match) {
    // إزالة الفواصل إن وجدت
    return parseFloat(match[0].replace(/,/g, ''));
  }
  return null;
}

// ==========================================
// المهمة 1: تنظيف ملفات الإكسيل واستخراج الأسقف
// ==========================================
function cleanExcelAndExtract() {
  const seedFile = path.join(__dirname, 'seed_data.json');
  
  // إذا تم استخراج البيانات مسبقاً، نقوم بقراءتها بدلاً من مسح الملفات الفارغة
  if (fs.existsSync(seedFile)) {
    console.log("✅ تم العثور على ملف البيانات المستخرجة مسبقاً (seed_data.json). سيتم استخدامه.");
    const data = JSON.parse(fs.readFileSync(seedFile, 'utf8'));
    return data;
  }

  const employersData = [];
  const files = fs.readdirSync(DIRECTORY_PATH);

  files.forEach(file => {
    if (!file.endsWith('.xlsx')) return;

    const filePath = path.join(DIRECTORY_PATH, file);
    const employerName = file.replace('.xlsx', '').trim();
    let ceilingFound = null;

    console.log(`\n📄 جارِ معالجة ملف: ${file}`);
    const workbook = xlsx.readFile(filePath);
    const sheetName = workbook.SheetNames[0];
    const worksheet = workbook.Sheets[sheetName];

    let modified = false;

    // المرور على كافة الخلايا للبحث عن "سقف عام"
    for (const cellAddress in worksheet) {
      if (cellAddress.startsWith('!')) continue;
      
      const cell = worksheet[cellAddress];
      if (cell && typeof cell.v === 'string' && cell.v.includes('سقف عام')) {
        ceilingFound = extractCeiling(cell.v);
        console.log(`   --> تم العثور على سقف عام: ${ceilingFound} د.ل في الخلية ${cellAddress}`);
        
        // مسح الكلمة من الخلية
        cell.v = '';
        if (cell.w) cell.w = '';
        modified = true;
      }
    }

    if (modified) {
      // حفظ الملف بعد تنظيفه
      xlsx.writeFile(workbook, filePath);
      console.log(`   --> تم مسح السقف من الملف وحفظه بنجاح.`);
    } else {
      console.log(`   --> لم يتم العثور على سقف عام.`);
    }

    // تجهيز بيانات جهة العمل للـ API
    employersData.push({
      name: employerName,
      code: `EMP-${employerName.substring(0, 4).toUpperCase()}-${Math.floor(Math.random() * 1000)}`,
      annualLimit: ceilingFound || 0 // إذا لم يوجد سقف، نضعه 0 أو القيمة الافتراضية
    });
  });

  // حفظ البيانات المستخرجة في ملف لاستخدامها لاحقاً
  fs.writeFileSync(seedFile, JSON.stringify(employersData, null, 2));
  console.log("✅ تم حفظ البيانات المستخرجة في seed_data.json");

  return employersData;
}

// ==========================================
// المهمة 2: الاتصال بالـ API لإنشاء جهات العمل والعقود
// ==========================================
async function seedDatabase(employersData) {
  try {
    console.log(`\n================================`);
    console.log(`🚀 بدء الاتصال بقاعدة البيانات (API)`);
    console.log(`================================`);

    // 1. تسجيل الدخول للحصول على التوكن
    console.log(`--> تسجيل الدخول كمدير...`);
    // تنويه: تأكد من صحة مسار الدخول (auth/login) بناء على إعدادات الـ Backend
    let token = '';
    try {
      const loginRes = await axios.post(`${API_BASE_URL}/auth/login`, ADMIN_CREDENTIALS);
      token = loginRes.data.data.token || loginRes.data.token;
      console.log(`   ✅ تم تسجيل الدخول بنجاح.`);
    } catch (err) {
      console.warn(`   ⚠️ تحذير: فشل تسجيل الدخول. سأحاول إرسال الطلبات بدون توكن (أو قم بتحديث مسار الـ Login).`);
    }

    const headers = token ? { Authorization: `Bearer ${token}` } : {};

    // 2. إنشاء جهات العمل والعقود
    for (const emp of employersData) {
      console.log(`\n------------------`);
      console.log(`🏢 إنشاء جهة العمل: ${emp.name}`);
      
      let employerId = null;
      
      // إنشاء Employer
      try {
        const empPayload = {
          code: emp.code,
          name: emp.name,
          active: true
        };
        const empRes = await axios.post(`${API_BASE_URL}/employers`, empPayload, { headers });
        employerId = empRes.data.data.id;
        console.log(`   ✅ تم إنشاء Employer بنجاح (ID: ${employerId})`);
      } catch (err) {
        const errMsg = err.response ? JSON.stringify(err.response.data) : err.message;
        console.error(`   ❌ فشل إنشاء Employer: ${errMsg}`);
        continue; // التخطي في حال الفشل
      }

      // إنشاء Benefit Policy
      if (employerId) {
        try {
          const year = new Date().getFullYear();
          const policyPayload = {
            name: `وثيقة ${emp.name}`,
            policyCode: `POL-${emp.code}`,
            employerOrgId: employerId,
            startDate: `${year}-01-01`,
            endDate: `${year}-12-31`,
            annualLimit: emp.annualLimit > 0 ? emp.annualLimit : 1000000, // السقف المستخرج
            defaultCoveragePercent: 100,
            status: 'ACTIVE'
          };
          
          const polRes = await axios.post(`${API_BASE_URL}/benefit-policies`, policyPayload, { headers });
          console.log(`   ✅ تم إنشاء عقد المنافع بنجاح مع سقف عام (${policyPayload.annualLimit}).`);
        } catch (err) {
          const errMsg = err.response ? JSON.stringify(err.response.data) : err.message;
          console.error(`   ❌ فشل إنشاء العقد: ${errMsg}`);
        }
      }
    }

    console.log(`\n🎉 اكتمل التنفيذ!`);

  } catch (error) {
    console.error("حدث خطأ رئيسي أثناء التنفيذ:", error.message);
  }
}

// ==========================================
// التشغيل الرئيسي
// ==========================================
(async () => {
  console.log("=== بدء تشغيل السكريبت ===");
  const employersData = cleanExcelAndExtract();
  
  // التشغيل الفعلي للدفع
  await seedDatabase(employersData);
  
})();
