"""The planner decides what passes; the operator executes bounded actions."""

from .client import Bridge
from .errors import BridgeError

__all__ = ["Bridge", "BridgeError"]
__version__ = "0.2.0"
