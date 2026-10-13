"""Hace importable el paquete nexus_ia al correr pytest desde cualquier carpeta."""
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
if str(RAIZ) not in sys.path:
    sys.path.insert(0, str(RAIZ))
