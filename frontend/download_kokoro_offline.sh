#!/usr/bin/env bash
# 下载 Kokoro 离线推理所需全部静态资源（模型权重 + voices + onnxruntime-web wasm）
# 来源：hf-mirror.com（本机直连 huggingface.co 被代理 502）
set -u
BASE="https://hf-mirror.com/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main"
ROOT="D:/WorkBuddy/GREStudy/frontend"
DST="$ROOT/public/kokoro/Kokoro-82M-v1.0-ONNX"
cd "$ROOT" || exit 1

echo "[1/5] tokenizer / config tiny files"
curl -sL --max-time 60 --fail "$BASE/tokenizer.json"      -o "$DST/tokenizer.json"      || echo "WARN tokenizer.json"
curl -sL --max-time 60 --fail "$BASE/tokenizer_config.json" -o "$DST/tokenizer_config.json" || echo "WARN tokenizer_config.json"

echo "[2/5] onnx model (dtype q8 -> model_quantized.onnx, ~92MB)"
curl -sL --max-time 900 --fail "$BASE/onnx/model_quantized.onnx" -o "$DST/onnx/model_quantized.onnx" || echo "WARN model_quantized.onnx"

echo "[3/5] voices (63 bins, ~33MB)"
VOICES=$(curl -s --max-time 60 "https://hf-mirror.com/api/models/onnx-community/Kokoro-82M-v1.0-ONNX/tree/main?recursive=true" \
  | grep -o '"path":"voices/[^"]*\.bin"' | sed 's/.*voices\///; s/"//g')
for v in $VOICES; do
  curl -sL --max-time 120 --fail "$BASE/voices/$v" -o "$DST/voices/$v" || echo "WARN voice $v"
done

echo "[4/5] onnxruntime-web wasm (local ort runtime, 离线用)"
cp node_modules/onnxruntime-web/dist/ort-wasm-simd-threaded.wasm       public/kokoro-ort/ || echo "WARN ort wasm"
cp node_modules/onnxruntime-web/dist/ort-wasm-simd-threaded.mjs        public/kokoro-ort/ || echo "WARN ort mjs"
cp node_modules/onnxruntime-web/dist/ort-wasm-simd-threaded.jsep.wasm  public/kokoro-ort/ || echo "WARN ort jsep wasm"
cp node_modules/onnxruntime-web/dist/ort-wasm-simd-threaded.jsep.mjs   public/kokoro-ort/ || echo "WARN ort jsep mjs"

echo "[5/5] verify"
echo "-- onnx --"; ls -l "$DST/onnx/"
echo "-- ort --";  ls -l public/kokoro-ort/
echo "-- voices count --"; ls "$DST/voices/" | wc -l
echo "DOWNLOAD DONE"
