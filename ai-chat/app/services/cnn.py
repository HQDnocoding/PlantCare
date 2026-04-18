import torch
import torch.nn as nn
from torchvision import transforms, models
from PIL import Image
from app.core.config import settings
import timm

CLASSES = [
    "Leaf_Algal",
    "Leaf_Blight",
    "Leaf_Colletotrichum",
    "Leaf_Healthy",
    "Leaf_Phomopsis",
    "Leaf_Rhizoctonia",
]


class CNNService:
    def __init__(self):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.model = self._load_model()
        self.transform = transforms.Compose(
            [
                transforms.Resize((256, 256)),
                transforms.ToTensor(),
                transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
            ]
        )

    def build_model(seld, model_name=None, num_classes=6, pretrained=False):
        model = timm.create_model(model_name, pretrained=pretrained, num_classes=0)

        n_feat = model.norm_head.num_features

        model.classifier = nn.Sequential(
            nn.Dropout(0.6),
            nn.Linear(n_feat, n_feat // 2),
            nn.ReLU6(inplace=True),
            nn.Dropout(0.5),
            nn.Linear(n_feat // 2, n_feat // 4),
            nn.ReLU6(inplace=True),
            nn.Dropout(0.45),
            nn.Linear(n_feat // 4, num_classes),
        )

        return model

    def _load_model(self) -> nn.Module:
        model = self.build_model(
            model_name=settings.model_name,
            num_classes=settings.num_classes,
            pretrained=False,
        )
        model.load_state_dict(
            torch.load(settings.model_weights_path_2, map_location=self.device)
        )
        model.eval()
        return model

    def predict(self, image_path: str) -> dict:
        image = Image.open(image_path).convert("RGB")
        tensor = self.transform(image).unsqueeze(0).to(self.device)

        with torch.no_grad():
            probs = torch.softmax(self.model(tensor), dim=1)[0]

        confidence, idx = probs.max(0)
        return {
            "class": CLASSES[idx.item()],
            "confidence": round(confidence.item(), 4),
            "confident_enough": confidence.item() >= settings.confidence_threshold,
        }


# Singleton
cnn_service = CNNService()
