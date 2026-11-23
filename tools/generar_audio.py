#!/usr/bin/env python3
"""
Genera los efectos de sonido y la música del juego (estilo chiptune) y los guarda en
app/src/main/res/raw como OGG Vorbis. Todo el audio es original y se crea por síntesis,
así que no depende de material con derechos de terceros.

Uso (desde la raíz del repositorio):  python3 tools/generar_audio.py
Requiere numpy y ffmpeg con libvorbis.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")

NOTES = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}


def freq(name: str) -> float:
    """Frecuencia de una nota en notación inglesa, p. ej. «C5» o «F#4» (A4 = 440 Hz)."""
    pitch, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((NOTES[pitch] + 12 * (octave - 4)) / 12)


def t(seconds: float) -> np.ndarray:
    return np.arange(int(SR * seconds)) / SR


def pulse(f: float, seconds: float, duty: float = 0.5) -> np.ndarray:
    phase = (t(seconds) * f) % 1.0
    return np.where(phase < duty, 1.0, -1.0)


def triangle(f: float, seconds: float) -> np.ndarray:
    phase = (t(seconds) * f) % 1.0
    return 4 * np.abs(phase - 0.5) - 1


def sine(f: float, seconds: float) -> np.ndarray:
    return np.sin(2 * np.pi * f * t(seconds))


def noise(seconds: float, seed: int = 1) -> np.ndarray:
    return np.random.default_rng(seed).uniform(-1, 1, int(SR * seconds))


def env(x: np.ndarray, attack: float = 0.005, release: float = 0.03, decay: float | None = None) -> np.ndarray:
    """Envolvente: subida y bajada cortas para evitar chasquidos; [decay] añade una caída exponencial."""
    n = len(x)
    e = np.ones(n)
    a = min(n, int(SR * attack))
    r = min(n, int(SR * release))
    if a > 0:
        e[:a] = np.linspace(0, 1, a)
    if r > 0:
        e[n - r:] *= np.linspace(1, 0, r)
    if decay:
        e *= np.exp(-np.arange(n) / (SR * decay))
    return x * e


def seq(notes, step: float, wave_fn, gap: float = 0.0, **kw) -> np.ndarray:
    parts = []
    for n in notes:
        tone = wave_fn(freq(n), step, **kw) if n else np.zeros(int(SR * step))
        parts.append(env(tone))
        if gap:
            parts.append(np.zeros(int(SR * gap)))
    return np.concatenate(parts)


def mix(*tracks) -> np.ndarray:
    n = max(len(x) for x in tracks)
    out = np.zeros(n)
    for x in tracks:
        out[: len(x)] += x
    return out


def normalize(x: np.ndarray, peak: float = 0.89) -> np.ndarray:
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def save(name: str, x: np.ndarray, quality: int = 4) -> None:
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    target = os.path.join(OUT, name + ".ogg")
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", str(quality), target],
        check=True,
    )
    os.remove(path)
    print(f"{target}: {os.path.getsize(target) / 1024:.1f} KB")


# ───────────────────────── Efectos ─────────────────────────

def effects() -> None:
    # Toque: chasquido breve.
    save("sfx_tap", normalize(env(pulse(1400, 0.035, 0.25), 0.001, 0.02, decay=0.012), 0.5))

    # Moneda: dos notas agudas, la segunda más larga.
    coin = np.concatenate([env(pulse(freq("B5"), 0.06, 0.5), 0.002, 0.005), env(pulse(freq("E6"), 0.22, 0.5), 0.002, 0.08, decay=0.09)])
    save("sfx_coin", normalize(coin, 0.55))

    # Compra: arpegio ascendente rápido.
    save("sfx_buy", normalize(seq(["C5", "E5", "G5", "C6"], 0.05, pulse, duty=0.25), 0.6))

    # Contratación: tres notas alegres con triángulo y un brillo de pulso.
    hire = mix(seq(["G4", "C5", "E5"], 0.09, triangle), 0.35 * seq(["G5", "C6", "E6"], 0.09, pulse, duty=0.125))
    save("sfx_hire", normalize(hire, 0.65))

    # Acierto: arpegio mayor y nota final sostenida.
    up = seq(["C5", "E5", "G5"], 0.07, pulse, duty=0.5)
    final = env(mix(pulse(freq("C6"), 0.32, 0.5), 0.5 * pulse(freq("G5"), 0.32, 0.25)), 0.003, 0.15, decay=0.25)
    save("sfx_correct", normalize(np.concatenate([up, final]), 0.6))

    # Fallo: dos notas que bajan con un poco de vibrato.
    def buzz(f, d):
        tt = t(d)
        vib = 1 + 0.02 * np.sin(2 * np.pi * 9 * tt)
        return env(np.where(((tt * f * vib) % 1.0) < 0.5, 1.0, -1.0), 0.003, 0.05)
    save("sfx_wrong", normalize(np.concatenate([buzz(freq("E4"), 0.16), buzz(freq("C4"), 0.32)]), 0.5))

    # Hito: fanfarria corta.
    fan = seq(["G4", "C5", "E5", "G5"], 0.07, pulse, duty=0.25)
    held = env(mix(pulse(freq("C6"), 0.45, 0.5), 0.6 * triangle(freq("C4"), 0.45), 0.4 * pulse(freq("E5"), 0.45, 0.125)), 0.003, 0.2, decay=0.4)
    save("sfx_levelup", normalize(np.concatenate([fan, held]), 0.7))

    # Mudanza: fanfarria larga con acordes.
    def chord(names, d, duty=0.25):
        return env(mix(*[pulse(freq(n), d, duty) for n in names]), 0.004, 0.08)
    move = np.concatenate([
        chord(["C5", "E5", "G5"], 0.14), np.zeros(int(SR * 0.04)),
        chord(["C5", "E5", "G5"], 0.1), chord(["D5", "F5", "A5"], 0.14),
        chord(["E5", "G5", "C6"], 0.14), env(mix(chord(["G5", "C6", "E6"], 0.7, 0.5), 0.6 * triangle(freq("C3"), 0.7)), 0.003, 0.3, decay=0.6),
    ])
    save("sfx_move", normalize(move, 0.7))

    # Aviso: dos pitidos alternos.
    save("sfx_alert", normalize(seq(["A5", "E5", "A5"], 0.08, pulse, gap=0.03, duty=0.5), 0.45))

    # Campanilla: notificación suave (seno con armónico).
    bell = mix(env(sine(freq("E6"), 0.6), 0.002, 0.1, decay=0.25), 0.4 * env(sine(freq("E6") * 2.76, 0.6), 0.002, 0.1, decay=0.08),
               np.concatenate([np.zeros(int(SR * 0.09)), env(sine(freq("B6"), 0.5), 0.002, 0.1, decay=0.2)]))
    save("sfx_chime", normalize(bell, 0.5))


# ───────────────────────── Música ─────────────────────────

BPM = 112
EIGHTH = 60 / BPM / 2

# Melodía: (nota, corcheas). Cada compás suma 8 corcheas; 16 compases en total.
MELODY = [
    # Parte A: C | Am | F | G (dos compases por acorde)
    ("E5", 2), ("G5", 1), ("E5", 1), ("D5", 2), ("C5", 2), ("D5", 1), ("E5", 1), ("G5", 2), ("E5", 4),
    ("A5", 2), ("G5", 1), ("E5", 1), ("C5", 2), ("E5", 2), ("D5", 1), ("C5", 1), ("A4", 2), ("C5", 4),
    ("F5", 2), ("A5", 1), ("G5", 1), ("F5", 2), ("E5", 2), ("D5", 1), ("E5", 1), ("F5", 2), ("A5", 4),
    ("G5", 2), ("F5", 1), ("E5", 1), ("D5", 2), ("B4", 2), ("C5", 1), ("D5", 1), ("E5", 2), ("D5", 4),
    # Parte B
    ("G5", 2), ("E5", 1), ("G5", 1), ("C6", 2), ("G5", 2), ("A5", 1), ("G5", 1), ("E5", 2), ("C5", 4),
    ("C6", 2), ("B5", 1), ("A5", 1), ("E5", 2), ("A5", 2), ("G5", 1), ("E5", 1), ("C5", 2), ("E5", 4),
    ("A5", 2), ("F5", 1), ("A5", 1), ("C6", 2), ("A5", 2), ("G5", 1), ("F5", 1), ("D5", 2), ("F5", 4),
    ("G5", 2), ("B5", 1), ("D6", 1), ("B5", 2), ("G5", 2), ("F5", 1), ("D5", 1), ("B4", 2), ("G4", 4),
]

CHORDS = [  # (fundamental del bajo, notas del acorde), dos compases cada uno
    ("C3", ["C4", "E4", "G4"]), ("A2", ["A3", "C4", "E4"]), ("F2", ["F3", "A3", "C4"]), ("G2", ["G3", "B3", "D4"]),
] * 2


def place(track: np.ndarray, sound: np.ndarray, start: int) -> None:
    """Suma [sound] en [track] desde [start], dando la vuelta al final para que el bucle no corte."""
    n = len(track)
    for i in range(0, len(sound), n):
        chunk = sound[i: i + n]
        s = (start + i) % n
        first = min(len(chunk), n - s)
        track[s: s + first] += chunk[:first]
        if first < len(chunk):
            track[: len(chunk) - first] += chunk[first:]


def music() -> None:
    total = int(round(16 * 8 * EIGHTH * SR))
    lead = np.zeros(total)
    bass = np.zeros(total)
    keys = np.zeros(total)
    drums = np.zeros(total)

    pos = 0.0
    for name, eighths in MELODY:
        d = eighths * EIGHTH
        tone = pulse(freq(name), d * 0.92, 0.25) * 0.8 + 0.2 * pulse(freq(name) * 1.003, d * 0.92, 0.5)
        place(lead, env(tone, 0.004, 0.04, decay=0.9), int(pos * SR))
        pos += d

    for c, (root, triad) in enumerate(CHORDS):
        bar0 = c * 2 * 8 * EIGHTH
        for bar in range(2):
            base = bar0 + bar * 8 * EIGHTH
            fifth = {"C3": "G2", "A2": "E2", "F2": "C3", "G2": "D3"}[root]
            octave = root[:-1] + str(int(root[-1]) + 1)
            pattern = [root, octave, fifth, octave, root, octave, fifth, octave]
            for k, n in enumerate(pattern):
                place(bass, env(triangle(freq(n), EIGHTH * 0.85), 0.003, 0.02), int((base + k * EIGHTH) * SR))
            # Acordes en las partes débiles (tiempos 2 y 4), cortos.
            for beat in (2, 6):
                stab = mix(*[pulse(freq(n), EIGHTH * 0.7, 0.125) for n in triad])
                place(keys, env(stab, 0.003, 0.05, decay=0.15), int((base + beat * EIGHTH) * SR))

    for bar in range(16):
        base = bar * 8 * EIGHTH
        for beat in (0, 4):  # bombo
            tt = t(0.14)
            kick = np.sin(2 * np.pi * np.cumsum(np.linspace(110, 45, len(tt))) / SR)
            place(drums, env(kick, 0.001, 0.03, decay=0.06) * 1.2, int((base + beat * EIGHTH) * SR))
        for beat in (2, 6):  # caja
            place(drums, env(noise(0.12, seed=bar + beat), 0.001, 0.03, decay=0.04) * 0.55, int((base + beat * EIGHTH) * SR))
        for beat in range(1, 8, 2):  # charles
            hat = np.diff(noise(0.035, seed=100 + bar * 8 + beat), prepend=0)
            place(drums, env(hat, 0.001, 0.01, decay=0.012) * 0.25, int((base + beat * EIGHTH) * SR))

    song = 0.24 * lead + 0.30 * bass + 0.09 * keys + 0.22 * drums
    save("music_office", normalize(song, 0.8), quality=3)


if __name__ == "__main__":
    effects()
    music()
