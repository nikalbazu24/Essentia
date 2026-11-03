# widescreen.rb — Essentia (GPLv2, like the rest of Essentia)
#
# Experimental 16:9 mode: widens the game's internal resolution instead of
# stretching the picture, so more of the map is visible on wide screens.
# Games that place menus or battle scenes for a fixed width may show those
# parts off-center; that's why it is an option per game.

module Graphics
  class << self
    ESSENTIA_RATIO = 16.0 / 9.0

    def essentia_wide_width(width, height)
      target = (height * ESSENTIA_RATIO).round
      target += 1 if target.odd?
      width < target ? target : width
    end

    alias_method :essentia_original_resize_screen, :resize_screen
    def resize_screen(width, height)
      essentia_original_resize_screen(essentia_wide_width(width, height), height)
    end
  end
end

# Games that never call resize_screen (classic RPG Maker XP at 640x480)
begin
  Graphics.resize_screen(Graphics.width, Graphics.height)
rescue StandardError
end
