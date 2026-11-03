# essentia_games.rb — Essentia (GPLv2, like the rest of Essentia)
#
# Fixes for things many RPG Maker / Pokémon Essentials games do that only work
# on a Windows PC. Loaded for every game, with both Ruby engines.
#
#  * Old Pokémon Essentials (up to v16, e.g. Pokémon Z) resizes its window with
#    Windows calls ("Sprite_Resizer") and shows its 512x384 picture inside the
#    640x480 RPG Maker XP screen. Here the screen simply takes the game's size,
#    and the window-size options of those games do nothing (the phone decides).
#  * Those games also take screenshots (for battle transitions) with a Windows
#    DLL; the engine's own screenshot is used instead.

module EssentiaGames
  @frame = 0

  class << self
    def graphics_meta
      class << Graphics; self; end
    end

    # The size the game is made for (what its own Graphics.width says)
    def game_size
      w = h = 0
      begin
        w = Graphics.send(:class_variable_get, :@@width).to_i
        h = Graphics.send(:class_variable_get, :@@height).to_i
      rescue Exception
      end
      if w <= 0 || h <= 0
        w = Object.const_defined?(:DEFAULTSCREENWIDTH) ? Object.const_get(:DEFAULTSCREENWIDTH).to_i : 0
        h = Object.const_defined?(:DEFAULTSCREENHEIGHT) ? Object.const_get(:DEFAULTSCREENHEIGHT).to_i : 0
      end
      return nil if w <= 0 || h <= 0
      [w, h]
    rescue Exception
      nil
    end

    def old_resizer?
      Object.private_method_defined?(:pbSetResizeFactor2) || Object.method_defined?(:pbSetResizeFactor2)
    end

    # Screen at the game's own size, no zoom, no offsets
    def normal_size
      return if @resizing
      @resizing = true
      $ResizeFactor = 1.0
      $ResizeFactorMul = 100
      $ResizeOffsetX = 0
      $ResizeOffsetY = 0
      $ResizeFactorSet = true
      size = game_size
      set_size(size[0], size[1]) if size
    rescue Exception
    ensure
      @resizing = false
    end

    def set_size(w, h)
      if defined?(EssentiaWide) && EssentiaWide.respond_to?(:base=)
        return if EssentiaWide.base == [w, h] && Graphics.essentia_raw_width == EssentiaWide.wide_w
        EssentiaWide.base = [w, h]
        Graphics.essentia_raw_resize_screen(*EssentiaWide.wide_size)
      else
        return if Graphics.essentia_native_width == w && Graphics.essentia_native_height == h
        Graphics.essentia_native_resize_screen(w, h)
      end
    rescue Exception
    end

    # The old window-size functions are replaced (once the game has defined them)
    def tame_resizer
      return if @taming || !old_resizer?
      current = Object.instance_method(:pbSetResizeFactor2) rescue nil
      return if current && @mine && current == @mine
      @taming = true
      Object.class_eval do
        def pbSetResizeFactor(*args); EssentiaGames.normal_size; end
        def pbSetResizeFactor2(*args); EssentiaGames.normal_size; end
        def pbConfigureFullScreen(*args); EssentiaGames.normal_size; end
        def pbConfigureWindowedScreen(*args); EssentiaGames.normal_size; end
        private :pbSetResizeFactor, :pbSetResizeFactor2, :pbConfigureFullScreen, :pbConfigureWindowedScreen
      end
      @mine = Object.instance_method(:pbSetResizeFactor2)
      @taming = false
      normal_size
    rescue Exception
    ensure
      @taming = false
    end

    # The engine's screenshot stays (the game may replace it with a DLL one)
    def keep_snapshot
      meta = graphics_meta
      return unless meta.method_defined?(:essentia_native_snap)
      current = meta.instance_method(:snap_to_bitmap) rescue nil
      return if current && @snap && current == @snap
      meta.send(:define_method, :snap_to_bitmap) { |*a| essentia_native_snap(*a) }
      @snap = meta.instance_method(:snap_to_bitmap)
    rescue Exception
    end

    def tick
      @frame += 1
      return unless @frame <= 2 || (@frame % 60).zero?
      tame_resizer
      keep_snapshot
    end
  end
end

module Graphics
  class << self
    alias_method :essentia_native_width, :width
    alias_method :essentia_native_height, :height
    alias_method :essentia_native_resize_screen, :resize_screen if method_defined?(:resize_screen)
    alias_method :essentia_native_snap, :snap_to_bitmap if method_defined?(:snap_to_bitmap)

    alias_method :essentia_games_original_update, :update
    def update(*args)
      essentia_games_original_update(*args)
      EssentiaGames.tick
    end
  end
end

# The game's scripts define the old resizer while loading, before the first
# frame: replace it right when it appears.
class << Object
  if method_defined?(:method_added) || private_method_defined?(:method_added)
    alias_method :essentia_games_method_added, :method_added
  end
  def method_added(name)
    essentia_games_method_added(name) if respond_to?(:essentia_games_method_added, true)
    EssentiaGames.tame_resizer if name.to_s == 'pbConfigureWindowedScreen'
  end
end
