"""YOLOv8n.pt -> yolov8n.tflite (640, fp32) export eder.

Kullanim (yerel):  pip install ultralytics tensorflow onnx tf-keras
                   python tools/export_yolo.py
CI bu betigi calistirir (model onbellekte yoksa).
"""
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "yolov8n.tflite"


def main() -> None:
    if OUT.exists():
        print("Model zaten var:", OUT)
        return
    from ultralytics import YOLO

    workdir = ROOT / ".yolo-export"
    workdir.mkdir(exist_ok=True)
    model = YOLO("yolov8n.pt")  # ilk calistirmada indirir
    exported = Path(
        model.export(format="tflite", imgsz=640, half=False, verbose=False)
    )
    if not exported.is_absolute():
        cand = workdir / exported.name
        if not exported.exists() and cand.exists():
            exported = cand
    if not exported.exists():
        found = list(ROOT.glob("**/yolov8n_float*.tflite"))
        if not found:
            print("Export ciktisi bulunamadi", file=sys.stderr)
            sys.exit(1)
        exported = found[0]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(exported, OUT)
    print("OK:", OUT, OUT.stat().st_size, "bayt")


if __name__ == "__main__":
    main()
