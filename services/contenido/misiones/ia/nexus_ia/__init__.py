"""IA de combate de misiones (HU-SIM-008): entrenamiento de la red propia.

El modelo se entrena aqui con PyTorch sobre la coleccion `eventos_de_combate`,
se exporta a ONNX y lo ejecuta el servicio de misiones en Java (ONNX Runtime).
"""
