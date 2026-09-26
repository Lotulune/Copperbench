import io


class DesktopBackend:
    platform = "unknown"

    def capture(self, window, max_width):
        import mss
        from PIL import Image
        rect = window["rect"]
        with mss.mss() as grabber:
            shot = grabber.grab(rect)
            picture = Image.frombytes("RGB", shot.size, shot.rgb)
        if picture.width > max_width:
            picture = picture.resize((max_width, max(1, round(picture.height * max_width / picture.width))), Image.Resampling.LANCZOS)
        output = io.BytesIO()
        picture.save(output, format="PNG")
        return output.getvalue(), picture.width, picture.height

    def close(self):
        self.release_all()
