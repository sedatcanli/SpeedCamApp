"""YOLOv8n.pt -> yolo.onnx (640) export eder.

Kullanim (yerel):  pip install ultralytics onnx
                   python tools/export_yolo.py
CI bu betigi calistirir (model onbellekte yoksa). TF zinciri YOK.
"""
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "yolo.onnx"


def main() -> None:
    if OUT.exists():
        print("Model zaten var:", OUT)
        return
    from ultralytics import YOLO

    model = YOLO("yolov8n.pt")  # ilk calistirmada indirir
    exported = Path(model.export(format="onnx", imgsz=640, verbose=False))
    if not exported.is_absolute():
        exported = Path.cwd() / exported
    if not exported.exists():
        found = [p for p in ROOT.rglob("yolov8n.onnx")]
        if not found:
            print("Export ciktisi bulunamadi", file=sys.stderr)
            sys.exit(1)
        exported = found[0]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(exported, OUT)
    print("OK:", OUT, OUT.stat().st_size, "bayt")


if __name__ == "__main__":
    main()
