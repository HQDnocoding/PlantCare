import firebase_admin
from firebase_admin import credentials, firestore, storage
from app.core.config import settings
import json


def init_firebase():
    if not firebase_admin._apps:
        cred_dict = json.loads(settings.firebase_credentials_json)
        cred = credentials.Certificate(cred_dict)
        firebase_admin.initialize_app(
            cred, {"storageBucket": settings.firebase_storage_bucket}
        )


init_firebase()

db = firestore.client()
bucket = storage.bucket()
