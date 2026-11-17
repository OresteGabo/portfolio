"""Protect lossless trimming, transparent filtering gutters and reproducible image geometry."""
import hashlib
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import unittest
import zlib

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from onboarding_png import SIGNATURE, chunk, decode, encode, paeth, trim


class OnboardingPngTests(unittest.TestCase):
    def test_crop_preserves_every_visible_pixel_and_filtering_gutter(self):
        width, height = 25, 23
        rows = [bytes((13, 25, 37, 0)) * width for _ in range(height)]
        for y in range(8, 14):
            row = bytearray(rows[y])
            for x in range(7, 16):
                row[x * 4:x * 4 + 4] = bytes((x * 7, y * 11, 89, 1 + (x * y) % 255))
            rows[y] = bytes(row)
        source = encode(width, height, rows, [(b'sRGB', b'\x00')])
        cropped, bounds = trim(source)
        self.assertEqual((25, 23, 3, 4, 17, 14), bounds)
        w, h, actual, metadata = decode(cropped)
        self.assertEqual((17, 14), (w, h))
        self.assertEqual([(b'sRGB', b'\x00')], metadata)
        for y, row in enumerate(rows):
            for x in range(width):
                pixel = row[x * 4:x * 4 + 4]
                if pixel[3]:
                    self.assertEqual(pixel, actual[y - bounds[3]][(x - bounds[2]) * 4:(x - bounds[2]) * 4 + 4])
        self.assertLess(w * h * 4, width * height * 4)

    def test_decoder_handles_all_five_png_filters(self):
        width, height = 7, 5
        rows = [bytes((x * 71 + y * 39) % 256 for x in range(width * 4)) for y in range(height)]
        raw = bytearray()
        previous = bytes(width * 4)
        for mode, row in enumerate(rows):
            raw.append(mode)
            for x, value in enumerate(row):
                left = row[x - 4] if x >= 4 else 0
                above = previous[x]
                upper_left = previous[x - 4] if x >= 4 else 0
                prediction = (0, left, above, (left + above) // 2, paeth(left, above, upper_left))[mode]
                raw.append((value - prediction) & 255)
            previous = row
        header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
        png = SIGNATURE + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
        self.assertEqual(rows, decode(png)[2])

    def test_opaque_canvas_keeps_original_extent(self):
        rows = [bytes((1, 2, 3, 255)) * 8 for _ in range(7)]
        cropped, bounds = trim(encode(8, 7, rows))
        self.assertEqual((8, 7, 0, 0, 8, 7), bounds)
        self.assertEqual(rows, decode(cropped)[2])

    def test_empty_layer_still_has_a_valid_transparent_bitmap(self):
        cropped, bounds = trim(encode(8, 7, [bytes(32)] * 7))
        self.assertEqual((8, 7, 0, 0, 1, 1), bounds)
        self.assertEqual([bytes(4)], decode(cropped)[2])

    def test_corrupt_png_is_rejected(self):
        png = bytearray(encode(1, 1, [bytes((1, 2, 3, 255))]))
        png[-6] ^= 1
        with self.assertRaisesRegex(ValueError, 'checksum'):
            decode(png)

    @unittest.skipUnless(shutil.which('djpeg'), 'Pixel identity check requires libjpeg djpeg')
    def test_signin_photo_keeps_approved_decoded_pixels(self):
        photo = ROOT / 'app/shared/src/commonMain/composeResources/drawable/kigali_assets_36653828.jpg'
        pixels = subprocess.run(['djpeg', '-dct', 'int', str(photo)], check=True, capture_output=True).stdout
        # Reference hash captured before lossless JPEG entropy/progressive optimization.
        self.assertEqual('4908fc3ecbe4587b466daca5caa33f38bd5040cd2c3164cccd3a83564fc04541',
                         hashlib.sha256(pixels).hexdigest())


if __name__ == '__main__':
    unittest.main()
