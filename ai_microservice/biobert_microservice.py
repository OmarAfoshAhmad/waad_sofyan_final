from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from transformers import AutoTokenizer, AutoModel
import torch
import torch.nn.functional as F

# 1. تهيئة تطبيق الـ API
app = FastAPI(title="WAAD BioBERT Microservice", version="1.0")

# 2. تحميل النموذج والقاموس وقت بدء تشغيل السيرفر
print("⏳ جاري تحميل نموذج ClinicalBERT... (قد يستغرق بعض الوقت في المرة الأولى)")
MODEL_NAME = "emilyalsentzer/Bio_ClinicalBERT"
tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
model = AutoModel.from_pretrained(MODEL_NAME)
print("✅ تم تحميل النموذج بنجاح!")

# 3. تعريف شكل البيانات المستقبلة (نفس الشكل الذي يرسله الجافا)
class ClassificationRequest(BaseModel):
    text: str

# كتالوج محاكاة (في الواقع ستقوم بتحميل هذا من قاعدة بياناتك)
STANDARD_CATALOG = {
    "CAT023": ["complete blood count", "blood test", "hemoglobin analysis"],
    "CAT024": ["appendectomy", "removal of appendix", "surgery for appendicitis"],
    "CAT010": ["x-ray of chest", "chest radiograph", "lung imaging"]
}

# حساب التضمين المتجهي للكتالوج (Pre-compute catalog embeddings)
def get_embedding(text):
    inputs = tokenizer(text, return_tensors="pt", padding=True, truncation=True)
    with torch.no_grad():
        outputs = model(**inputs)
    # أخذ متوسط المتجهات (Mean Pooling)
    return outputs.last_hidden_state.mean(dim=1)

catalog_embeddings = {}
for category, keywords in STANDARD_CATALOG.items():
    embeddings = [get_embedding(kw) for kw in keywords]
    catalog_embeddings[category] = embeddings

@app.post("/predict")
async def predict_medical_service(request: ClassificationRequest):
    if not request.text:
        raise HTTPException(status_code=400, detail="Text cannot be empty")
        
    # 1. تحويل نص المستشفى إلى متجه رياضي (Embedding)
    input_embedding = get_embedding(request.text)
    
    best_category = None
    best_score = 0.0
    
    # 2. قياس مدى التشابه (Cosine Similarity) مع الكتالوج القياسي
    for category, embeddings in catalog_embeddings.items():
        for cat_emb in embeddings:
            similarity = F.cosine_similarity(input_embedding, cat_emb).item()
            if similarity > best_score:
                best_score = similarity
                best_category = category
                
    # 3. إرجاع النتيجة للجافا
    return {
        "category": best_category if best_score > 0.80 else None, # نرجع التصنيف فقط إذا كانت الثقة عالية
        "confidence": round(best_score, 4),
        "medicalMeaning": f"Matched using BioBERT vector similarity ({best_score*100:.1f}%)"
    }

# لتشغيل السيرفر أثناء التطوير:
# uvicorn biobert_microservice:app --reload --port 8000
