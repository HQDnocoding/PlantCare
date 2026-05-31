"""
Centralized logging configuration for ELK Stack integration.
Sends JSON logs to Logstash via TCP socket.
"""

import logging
import logging.handlers
import os
import socket

from pythonjsonlogger import jsonlogger


def _make_json_formatter(service_name: str) -> jsonlogger.JsonFormatter:
    class _Formatter(jsonlogger.JsonFormatter):
        def add_fields(self, log_record, record, message_dict):
            super().add_fields(log_record, record, message_dict)
            log_record["service"] = service_name
            log_record["environment"] = os.getenv("ENVIRONMENT", "local")
            log_record["hostname"] = socket.gethostname()

    return _Formatter(fmt="%(asctime)s %(levelname)s %(name)s %(message)s")


def setup_logging(
    service_name: str = "ai-chat-service", log_level: str = "INFO"
) -> logging.Logger:
    """
    Configure logging with JSON formatter for ELK Stack.

    Args:
        service_name: Service identifier for log aggregation
        log_level: Logging level (INFO, DEBUG, WARNING, ERROR, CRITICAL)
    """
    level = getattr(logging, log_level.upper(), logging.INFO)

    root_logger = logging.getLogger()
    root_logger.setLevel(level)
    root_logger.handlers.clear()

    # ── Console ──────────────────────────────────────────────────────────────
    console_handler = logging.StreamHandler()
    console_handler.setLevel(level)
    console_handler.setFormatter(
        logging.Formatter(
            fmt="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        )
    )
    root_logger.addHandler(console_handler)

    # ── File ─────────────────────────────────────────────────────────────────
    log_dir = os.getenv("LOG_DIR", "./logs")
    os.makedirs(log_dir, exist_ok=True)

    file_handler = logging.handlers.RotatingFileHandler(
        filename=os.path.join(log_dir, "app.log"),
        maxBytes=10 * 1024 * 1024,  # 10 MB
        backupCount=30,
        encoding="utf-8",
    )
    file_handler.setLevel(level)
    file_handler.setFormatter(_make_json_formatter(service_name))
    root_logger.addHandler(file_handler)

    # ── Logstash TCP (mặc định: localhost:5000) ────────────────────────────
    logstash_host = os.getenv("LOGSTASH_HOST", "localhost")
    logstash_port = int(os.getenv("LOGSTASH_PORT", 5000))

    log = logging.getLogger(__name__)

    try:
        logstash_handler = logging.handlers.SocketHandler(logstash_host, logstash_port)
        logstash_handler.setLevel(level)
        logstash_handler.setFormatter(_make_json_formatter(service_name))
        root_logger.addHandler(logstash_handler)
        log.info("Logstash handler configured: %s:%d", logstash_host, logstash_port)
    except Exception as e:
        log.warning(
            "Failed to setup Logstash handler: %s. "
            "Logs will only go to console and file.",
            e,
        )

    return root_logger


def get_logger(name: str) -> logging.Logger:
    """
    Get a logger instance with the given name.

    Args:
        name: Logger name (typically __name__)
    """
    return logging.getLogger(name)
