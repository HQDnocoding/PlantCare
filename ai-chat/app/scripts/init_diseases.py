"""
Script to initialize disease database with sample data
Run: python -m app.scripts.init_diseases
"""

from sqlalchemy.orm import Session
from app.core.database import SessionLocal, engine, Base
from app.models.disease import Disease, Medicine
import logging

logger = logging.getLogger(__name__)


def init_diseases():
    """Initialize diseases database"""
    Base.metadata.create_all(bind=engine)
    db = SessionLocal()

    try:
        if db.query(Disease).count() > 0:
            print("✓ Diseases already initialized. Skipping...")
            return

        # ── 1. Định nghĩa medicines dùng chung ──────────────────────────────
        medicines_data = {
            "copper_oxychloride": {
                "name": "Copper Oxychloride 50WP",
                "active_ingredient": "Copper Oxychloride 50%",
                "formulation": "WP (Wettable Powder)",
                "usage": "Pha 30-40g/10 lít nước, phun ướt đều bề mặt lá cả hai mặt. Phun vào sáng sớm hoặc chiều mát.",
                "dosage": "3-4 kg/ha, phun 2-3 lần cách nhau 7-10 ngày",
                "weather_condition": "Phun khi trời không mưa, tránh phun lúc nhiệt độ cao trên 35°C.",
                "toxicity": "Độc tính nhóm III (WHO) - Ít độc",
                "safety_warnings": [
                    "Đeo khẩu trang, kính bảo hộ và găng tay khi pha và phun",
                    "Không ăn uống trong khi phun thuốc",
                    "Rửa tay sạch sau khi tiếp xúc",
                    "Không phun gần nguồn nước, ao cá",
                ],
                "pre_harvest_interval": "7 ngày",
            },
            "bordeaux": {
                "name": "Bordeaux mixture",
                "active_ingredient": "Copper sulfate + Calcium hydroxide",
                "formulation": "Solution",
                "usage": "Pha theo tỉ lệ 1:1:100 (CuSO4 : Ca(OH)2 : nước). Phun phủ đều bề mặt lá.",
                "dosage": "Pha nồng độ 1%, phun 2 lần cách nhau 10-14 ngày",
                "weather_condition": "Phun khi trời khô ráo, không mưa trong 4-6 giờ sau phun.",
                "toxicity": "Độc tính nhóm III (WHO) - Ít độc",
                "safety_warnings": [
                    "Không pha chung với thuốc có tính kiềm khác",
                    "Tránh tiếp xúc da và mắt",
                    "Không dùng dụng cụ kim loại để pha thuốc",
                ],
                "pre_harvest_interval": "14 ngày",
            },
            "mancozeb": {
                "name": "Mancozeb 80WP",
                "active_ingredient": "Mancozeb 80%",
                "formulation": "WP (Wettable Powder)",
                "usage": "Pha 25-30g/10 lít nước, phun đều hai mặt lá.",
                "dosage": "2-2.5 kg/ha, phun 2-3 lần",
                "weather_condition": "Phun khi trời không mưa, nhiệt độ dưới 32°C.",
                "toxicity": "Độc tính nhóm III (WHO) - Ít độc",
                "safety_warnings": [
                    "Đeo khẩu trang lọc bụi khi pha thuốc",
                    "Tránh hít phải bụi thuốc",
                    "Nguy hiểm cho sinh vật dưới nước",
                ],
                "pre_harvest_interval": "10 ngày",
            },
            "ridomil": {
                "name": "Ridomil Gold 68WG",
                "active_ingredient": "Metalaxyl-M 4% + Mancozeb 64%",
                "formulation": "WG",
                "usage": "Pha 25g/10 lít nước, phun ướt đều lá.",
                "dosage": "2.5-3 kg/ha, phun 2 lần cách nhau 10-14 ngày",
                "weather_condition": "Phun khi trời khô ráo, không mưa trong 6 giờ.",
                "toxicity": "Độc tính nhóm III (WHO) - Ít độc",
                "safety_warnings": [
                    "Đeo đồ bảo hộ đầy đủ khi phun",
                    "Không phun khi có gió mạnh",
                    "Luân phiên với thuốc khác nhóm để tránh kháng thuốc",
                ],
                "pre_harvest_interval": "14 ngày",
            },
            "aliette": {
                "name": "Aliette 800WG",
                "active_ingredient": "Fosetyl-aluminium 80%",
                "formulation": "WG",
                "usage": "Pha 30-40g/10 lít nước. Có thể phun lá hoặc tưới gốc.",
                "dosage": "3-4 kg/ha, phun 2-3 lần cách nhau 7-10 ngày",
                "weather_condition": "Phun trong điều kiện thời tiết bình thường.",
                "toxicity": "Độc tính nhóm IV (WHO) - Rất ít độc",
                "safety_warnings": [
                    "Tương đối an toàn nhưng vẫn cần đeo bảo hộ",
                    "Không pha chung với thuốc có tính acid",
                ],
                "pre_harvest_interval": "3 ngày",
            },
            "antracol": {
                "name": "Antracol 70WP",
                "active_ingredient": "Propineb 70%",
                "formulation": "WP",
                "usage": "Pha 25-30g/10 lít nước, phun ướt đều hai mặt lá.",
                "dosage": "2.5-3 kg/ha",
                "weather_condition": "Phun khi trời không mưa, tránh phun khi nắng gắt.",
                "toxicity": "Độc tính nhóm III (WHO) - Ít độc",
                "safety_warnings": [
                    "Đeo khẩu trang và găng tay khi pha thuốc",
                    "Không thải bỏ vào nguồn nước",
                ],
                "pre_harvest_interval": "7 ngày",
            },
            "score": {
                "name": "Score 250EC",
                "active_ingredient": "Difenoconazole 250g/L",
                "formulation": "EC",
                "usage": "Pha 10ml/10 lít nước, phun ướt đều.",
                "dosage": "0.5-1 lít/ha, phun 2 lần cách nhau 7-10 ngày",
                "weather_condition": "Phun khi thời tiết khô ráo, không mưa 4-6 giờ sau phun.",
                "toxicity": "Độc tính nhóm II (WHO) - Độc vừa",
                "safety_warnings": [
                    "Mặc đồ bảo hộ đầy đủ: áo dài tay, găng tay, khẩu trang",
                    "Nguy hiểm cho cá và sinh vật thủy sinh",
                ],
                "pre_harvest_interval": "14 ngày",
            },
            "amistar": {
                "name": "Amistar 250SC",
                "active_ingredient": "Azoxystrobin 250g/L",
                "formulation": "SC",
                "usage": "Pha 10ml/10 lít nước. Phun phủ đều bề mặt lá.",
                "dosage": "0.5-0.75 lít/ha",
                "weather_condition": "Phun trong điều kiện thời tiết bình thường.",
                "toxicity": "Độc tính nhóm IV (WHO) - Rất ít độc",
                "safety_warnings": [
                    "Luân phiên với thuốc nhóm khác để tránh kháng thuốc",
                    "Không phun quá 3 lần/vụ",
                ],
                "pre_harvest_interval": "7 ngày",
            },
            "rovral": {
                "name": "Rovral 50WP",
                "active_ingredient": "Iprodione 50%",
                "formulation": "WP",
                "usage": "Pha 20-25g/10 lít nước, phun ướt đều.",
                "dosage": "1.5-2 kg/ha, phun 2 lần cách nhau 7-10 ngày",
                "weather_condition": "Phun khi trời không mưa.",
                "toxicity": "Độc tính nhóm III (WHO) - Ít độc",
                "safety_warnings": [
                    "Đeo khẩu trang và găng tay",
                    "Bảo quản tránh xa trẻ em",
                ],
                "pre_harvest_interval": "7 ngày",
            },
            "topsin": {
                "name": "Topsin M 70WP",
                "active_ingredient": "Thiophanate-methyl 70%",
                "formulation": "WP",
                "usage": "Pha 10-15g/10 lít nước, phun phủ đều.",
                "dosage": "1-1.5 kg/ha",
                "weather_condition": "Phun trong điều kiện thời tiết bình thường.",
                "toxicity": "Độc tính nhóm III (WHO) - Ít độc",
                "safety_warnings": [
                    "Luân phiên với thuốc nhóm khác",
                    "Tránh ô nhiễm nguồn nước",
                ],
                "pre_harvest_interval": "14 ngày",
            },
            "validacin": {
                "name": "Validacin 5L",
                "active_ingredient": "Validamycin 5%",
                "formulation": "SL",
                "usage": "Pha 20-30ml/10 lít nước, phun ướt đều tán lá và tưới vào gốc.",
                "dosage": "1.5-2 lít/ha, phun 2-3 lần cách nhau 7 ngày",
                "weather_condition": "Có thể phun trong nhiều điều kiện thời tiết.",
                "toxicity": "Độc tính nhóm IV (WHO) - Rất ít độc",
                "safety_warnings": [
                    "An toàn, ít độc với người và môi trường",
                    "Vẫn cần đeo bảo hộ cơ bản khi phun",
                ],
                "pre_harvest_interval": "3 ngày",
            },
            "anvil": {
                "name": "Anvil 5SC",
                "active_ingredient": "Hexaconazole 50g/L",
                "formulation": "SC",
                "usage": "Pha 20ml/10 lít nước, phun ướt đều.",
                "dosage": "1 lít/ha",
                "weather_condition": "Phun khi thời tiết khô ráo.",
                "toxicity": "Độc tính nhóm II (WHO) - Độc vừa",
                "safety_warnings": [
                    "Mặc đồ bảo hộ đầy đủ khi phun",
                    "Nguy hiểm cho cá",
                    "Không phun gần ao hồ",
                ],
                "pre_harvest_interval": "21 ngày",
            },
            "monceren": {
                "name": "Monceren 250SC",
                "active_ingredient": "Pencycuron 250g/L",
                "formulation": "SC",
                "usage": "Pha 15-20ml/10 lít nước. Đặc trị Rhizoctonia.",
                "dosage": "0.75-1 lít/ha",
                "weather_condition": "Phun trong thời tiết bình thường.",
                "toxicity": "Độc tính nhóm III (WHO) - Ít độc",
                "safety_warnings": [
                    "Đeo khẩu trang và găng tay",
                    "Không thải vào nguồn nước",
                ],
                "pre_harvest_interval": "14 ngày",
            },
        }

        # ── 2. Định nghĩa diseases và medicines keys liên kết ───────────────
        disease_data = [
            {
                "class_name": "Leaf_Healthy",
                "order": 0,
                "name": "Lá khỏe mạnh",
                "description": "Lá cây sầu riêng phát triển bình thường, có màu xanh đậm bóng, không có dấu hiệu bệnh lý hay sâu hại.",
                "symptoms": [
                    "Lá có màu xanh đậm, bóng đều",
                    "Không có đốm, vết hoại tử hay biến màu",
                    "Lá cứng cáp, không bị cong vênh",
                ],
                "cause": "Không có tác nhân gây bệnh.",
                "favorable_conditions": "Không áp dụng.",
                "treatment": "Không cần điều trị.",
                "prevention": "Duy trì chế độ tưới nước, bón phân hợp lý.",
                "medicine_keys": [],  #  Không dùng thuốc
            },
            {
                "class_name": "Leaf_Algal",
                "order": 1,
                "name": "Bệnh tảo đỏ",
                "description": "Bệnh do tảo ký sinh Cephaleuros virescens gây ra, tạo thành các đốm sần sùi màu cam đỏ trên bề mặt lá.",
                "symptoms": [
                    "Xuất hiện đốm tròn màu cam, đỏ gỉ sắt trên mặt lá",
                    "Bề mặt đốm sần sùi như nhung",
                    "Lá bị nặng có thể vàng và rụng sớm",
                ],
                "cause": "Do tảo ký sinh Cephaleuros virescens.",
                "favorable_conditions": "Độ ẩm cao trên 80%, mưa nhiều kéo dài.",
                "treatment": "Cắt tỉa cành lá tạo thông thoáng, phun thuốc gốc đồng 2-3 lần cách nhau 7-10 ngày.",
                "prevention": "Tỉa cành định kỳ, tránh tưới nước lên tán lá.",
                "medicine_keys": ["copper_oxychloride", "bordeaux", "mancozeb"],
            },
            {
                "class_name": "Leaf_Blight",
                "order": 2,
                "name": "Bệnh cháy lá",
                "description": "Bệnh cháy lá gây thiệt hại lớn, tạo ra các vết cháy lan nhanh, có thể làm rụng toàn bộ lá.",
                "symptoms": [
                    "Vết bệnh bắt đầu từ chóp lá, màu nâu vàng",
                    "Vết cháy lan nhanh vào phiến lá",
                    "Lá bị nặng cháy toàn bộ và rụng",
                ],
                "cause": "Do nấm Phytophthora palmivora hoặc vi khuẩn Xanthomonas campestris.",
                "favorable_conditions": "Mưa nhiều, độ ẩm trên 85%, nhiệt độ 25-30°C.",
                "treatment": "Cắt bỏ lá bệnh, phun thuốc đặc trị ngay khi thấy triệu chứng.",
                "prevention": "Cải thiện thoát nước vườn, tỉa cành tạo thông thoáng.",
                "medicine_keys": [
                    "ridomil",
                    "aliette",
                    "mancozeb",
                ],  #  mancozeb dùng chung với Leaf_Algal
            },
            {
                "class_name": "Leaf_Colletotrichum",
                "order": 3,
                "name": "Bệnh thán thư",
                "description": "Bệnh thán thư tấn công lá, cành non và trái, gây các vết đốm hoại tử điển hình.",
                "symptoms": [
                    "Vết đốm tròn màu nâu đến đen trên lá",
                    "Viền vết bệnh có quầng vàng rõ ràng",
                    "Lá bị nặng khô và rụng sớm",
                ],
                "cause": "Do nấm Colletotrichum gloeosporioides.",
                "favorable_conditions": "Nhiệt độ 25-30°C, độ ẩm cao, mưa nhiều.",
                "treatment": "Thu gom tiêu hủy lá bệnh, phun thuốc trừ nấm đặc trị.",
                "prevention": "Bón phân đầy đủ, tưới nước hợp lý, phun phòng trước mùa mưa.",
                "medicine_keys": ["antracol", "score", "amistar"],
            },
            {
                "class_name": "Leaf_Phomopsis",
                "order": 4,
                "name": "Bệnh Phomopsis",
                "description": "Bệnh gây hoại tử lá từ mép lá vào trong, làm lá chết dần và rụng sớm.",
                "symptoms": [
                    "Vết bệnh từ mép lá, màu nâu xám đến nâu sẫm",
                    "Bề mặt vết bệnh có các chấm đen nhỏ",
                    "Lá bị nặng chết khô toàn bộ",
                ],
                "cause": "Do nấm Phomopsis sp.",
                "favorable_conditions": "Mùa mưa, độ ẩm cao, cây bị stress.",
                "treatment": "Tỉa cành, cắt bỏ bộ phận bệnh nặng, phun thuốc trị nấm.",
                "prevention": "Tỉa cành định kỳ, bón phân đầy đủ sau thu hoạch.",
                "medicine_keys": [
                    "rovral",
                    "topsin",
                    "amistar",
                ],  #  amistar dùng chung
            },
            {
                "class_name": "Leaf_Rhizoctonia",
                "order": 5,
                "name": "Bệnh Rhizoctonia",
                "description": "Bệnh thường xuất hiện ở tầng lá thấp gần mặt đất, lây lan nhanh trong điều kiện ẩm.",
                "symptoms": [
                    "Vết bệnh màu nâu đỏ đến nâu sẫm",
                    "Bề mặt vết bệnh có thể thấy sợi nấm màu nâu",
                    "Lá héo và rụng nhanh",
                ],
                "cause": "Do nấm đất Rhizoctonia solani.",
                "favorable_conditions": "Đất ẩm ướt liên tục, vườn rậm rạp, nhiệt độ 28-32°C.",
                "treatment": "Cải thiện thoát nước, tỉa cành, xử lý đất định kỳ.",
                "prevention": "Lên luống cao thoát nước, không trồng quá dày.",
                "medicine_keys": ["validacin", "anvil", "monceren"],
            },
        ]

        # ── 3. Tạo tất cả Medicine objects trước ────────────────────────────
        medicine_objects = {}
        for key, med_data in medicines_data.items():
            medicine = Medicine(**med_data)
            db.add(medicine)
            medicine_objects[key] = medicine

        db.flush()  # Lấy ID cho tất cả medicines

        # ── 4. Tạo Disease và gán medicines qua many-to-many ────────────────
        for disease_info in disease_data:
            medicine_keys = disease_info.pop("medicine_keys", [])

            disease = Disease(
                **disease_info,
                version="1.0.0",
            )

            # Gán medicines (many-to-many)
            disease.medicines = [medicine_objects[k] for k in medicine_keys]

            db.add(disease)

        db.commit()

        print(f"✓ Successfully initialized {db.query(Disease).count()} diseases")
        print(f"✓ Successfully initialized {db.query(Medicine).count()} medicines")

    except Exception as e:
        db.rollback()
        print(f"✗ Error initializing diseases: {e}")
        raise
    finally:
        db.close()


if __name__ == "__main__":
    init_diseases()
