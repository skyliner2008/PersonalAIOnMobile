#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Compatibility shim: the generator now lives in tools/avatar/ (one file per mood /
scene story, see tools/avatar/build.py). Kept so `python tools/build_scene.py [out.rml]`
still works."""
import os
import runpy
import sys

runpy.run_path(os.path.join(os.path.dirname(os.path.abspath(__file__)), "avatar", "build.py"),
               run_name="__main__")
