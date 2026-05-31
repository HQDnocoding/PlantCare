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
        import logging

        logger = logging.getLogger(__name__)
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        logger.info(f"CNN Service using device: {self.device}")
        try:
            self.model = self._load_model()
            logger.info("CNN model loaded successfully")
        except Exception as e:
            logger.error(
                f"Failed to load CNN model: {type(e).__name__}: {e}", exc_info=True
            )
            raise
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
        import logging
        import os

        logger = logging.getLogger(__name__)

        logger.info(f"Loading model: {settings.model_name}")
        logger.info(f"Model weights path: {settings.model_weights_path_2}")
        logger.info(f"Working directory: {os.getcwd()}")

        # Check if weights file exists
        if not os.path.exists(settings.model_weights_path_2):
            raise FileNotFoundError(
                f"Model weights not found: {settings.model_weights_path_2}"
            )

        model = self.build_model(
            model_name=settings.model_name,
            num_classes=settings.num_classes,
            pretrained=False,
        )
        try:
            weights = torch.load(
                settings.model_weights_path_2, map_location=self.device
            )
            logger.info(f"Loaded weights with keys: {list(weights.keys())[:5]}...")
            model.load_state_dict(weights)
            logger.info("Model weights loaded successfully")
        except Exception as e:
            logger.error(
                f"Failed to load weights: {type(e).__name__}: {e}", exc_info=True
            )
            raise
        model.eval()
        return model

    def predict(self, image_path: str) -> dict:
        import logging
        import os

        logger = logging.getLogger(__name__)

        try:
            logger.info(f"Opening image: {image_path}")
            if not os.path.exists(image_path):
                raise FileNotFoundError(f"Image not found: {image_path}")

            image = Image.open(image_path).convert("RGB")
            logger.info(f"Image loaded: {image.size}")

            tensor = self.transform(image).unsqueeze(0).to(self.device)
            logger.info(f"Tensor shape: {tensor.shape}")

            with torch.no_grad():
                probs = torch.softmax(self.model(tensor), dim=1)[0]

            confidence, idx = probs.max(0)
            result = {
                "class": CLASSES[idx.item()],
                "confidence": round(confidence.item(), 4),
                "confident_enough": confidence.item() >= settings.confidence_threshold,
            }
            logger.info(f"Prediction result: {result}")
            return result
        except Exception as e:
            logger.error(
                f"Prediction failed for {image_path}: {type(e).__name__}: {e}",
                exc_info=True,
            )
            raise


# Singleton
cnn_service = CNNService()
