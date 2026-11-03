# widescreen.rb — Essentia (GPLv2, like the rest of Essentia)
#
# "Smart" widescreen. Fan games draw their menus, battles and title screens
# for a fixed 4:3 width, so simply widening the game leaves those parts stuck
# to the left. Instead, Essentia switches the resolution on the fly:
#
#   * walking on the map  -> wide (more of the map is visible, player centered)
#   * menus, bag, party, battles, title, save screens -> the game's own 4:3
#     size, centered, with Essentia's ambient borders on the sides
#
# Switches happen while the screen is faded to black, so they don't show.

module EssentiaWide
  @base = nil        # [w, h] the game asked for
  @on = false        # is the wide resolution active?
  @depth = 0         # inside a menu/battle opened from the map
  @center = {}       # original camera constants
  @wrapped = {}
  @frame = 0
  @failed = false

  class << self
    attr_reader :on

    def aspect
      a = (ENV['ESSENTIA_ASPECT'] || '1.7778').to_f
      a = 16.0 / 9.0 if a < 1.4
      a = 2.2 if a > 2.2
      a
    end

    def base
      @base ||= [Graphics.width, Graphics.height]
    end

    def base=(size)
      @base = size
    end

    def wide_size
      w, h = base
      ww = (h * aspect).round
      ww += 1 if ww.odd?
      [ww > w ? ww : w, h]
    end

    def current_scene
      return SceneManager.scene if defined?(SceneManager) && SceneManager.respond_to?(:scene)
      $scene
    end

    def on_map?
      defined?(Scene_Map) && current_scene.is_a?(Scene_Map)
    end

    def in_battle?
      $game_temp && $game_temp.respond_to?(:in_battle) && $game_temp.in_battle
    rescue StandardError
      false
    end

    def want_wide?
      @depth.zero? && on_map? && !in_battle?
    end

    # Switches between the wide and the game's own resolution.
    def apply(wide)
      return if @failed || wide == @on
      w, h = wide ? wide_size : base
      return if w == base[0] && wide # nothing to widen (game already wide)
      @on = wide
      Graphics.essentia_raw_resize_screen(w, h)
      expand_viewports(w) if wide
      fix_camera(wide)
    rescue StandardError => e
      @failed = true
      @on = false
      Graphics.essentia_raw_resize_screen(*base) rescue nil
      print_error(e)
    end

    def sync
      apply(want_wide?)
    end

    # Menus and battles opened from the map run at the game's own size.
    def inside
      @depth += 1
      apply(false)
      yield
    ensure
      @depth -= 1
      sync
    end

    # Full-screen viewports made for the 4:3 size (map layers, message boxes)
    # grow to the wide size.
    def expand_viewports(width)
      bw, bh = base
      ObjectSpace.each_object(Viewport) do |v|
        next if (v.disposed? rescue true)
        r = v.rect
        next unless r.x == 0 && r.y == 0 && r.width == bw && r.height == bh
        v.rect = Rect.new(0, 0, width, bh)
      end
    rescue StandardError
    end

    # Pokémon Essentials / RPG Maker keep the player centered with constants
    # computed for the 4:3 width (4 map units per pixel, half the extra width).
    def fix_camera(wide)
      return unless defined?(Game_Player)
      extra = wide ? (wide_size[0] - base[0]) : 0
      [:SCREEN_CENTER_X, :CENTER_X].each do |name|
        next unless Game_Player.const_defined?(name, false)
        @center[name] = Game_Player.const_get(name) unless @center.key?(name)
        value = @center[name] + extra * 2
        Game_Player.send(:remove_const, name)
        Game_Player.const_set(name, value)
      end
      if wide && $game_player && $game_player.respond_to?(:center)
        $game_player.center($game_player.x, $game_player.y)
      end
    rescue StandardError
    end

    # Wraps game methods that open a menu or battle with a block, once the
    # game's scripts have defined them (and again if a plugin redefines them).
    def install_hooks
      [:pbFadeOutIn, :pbBattleAnimation].each { |m| wrap_block_method(m) }
      wrap_init(:Spriteset_Map)
      wrap_init(:TilemapRenderer)
    end

    def wrap_block_method(name)
      return unless Object.private_method_defined?(name) || Object.method_defined?(name)
      current = Object.instance_method(name)
      return if @wrapped[name] == current
      raw = :"essentia_wide_raw_#{name}"
      Object.send(:alias_method, raw, name)
      Object.send(:define_method, name) do |*args, &block|
        if block
          __send__(raw, *args) { |*a| EssentiaWide.inside { block.call(*a) } }
        else
          __send__(raw, *args)
        end
      end
      Object.send(:private, name)
      @wrapped[name] = Object.instance_method(name)
    rescue StandardError
    end

    # The map layers must be created at the wide size.
    def wrap_init(class_name)
      return unless Object.const_defined?(class_name)
      klass = Object.const_get(class_name)
      return unless klass.is_a?(Class)
      current = klass.instance_method(:initialize)
      return if @wrapped[class_name] == current
      raw = :essentia_wide_raw_initialize
      klass.send(:alias_method, raw, :initialize)
      klass.send(:define_method, :initialize) do |*args, &block|
        EssentiaWide.apply(true) if EssentiaWide.depth_zero?
        __send__(raw, *args, &block)
      end
      klass.send(:private, :initialize)
      @wrapped[class_name] = klass.instance_method(:initialize)
    rescue StandardError
    end

    def depth_zero?
      @depth.zero?
    end

    def tick
      @frame += 1
      install_hooks if @frame == 1 || (@frame % 120).zero?
      sync
    end

    def print_error(e)
      STDERR.puts("[Essentia] widescreen off: #{e.class}: #{e.message}") rescue nil
    end
  end
end

module Graphics
  class << self
    alias_method :essentia_raw_resize_screen, :resize_screen

    # The size the game asks for is its "own" size; while on the map Essentia
    # keeps the wide version of it.
    def resize_screen(width, height)
      EssentiaWide.base = [width, height]
      if EssentiaWide.on
        essentia_raw_resize_screen(*EssentiaWide.wide_size)
      else
        essentia_raw_resize_screen(width, height)
      end
    end

    alias_method :essentia_wide_original_update, :update
    def update(*args)
      essentia_wide_original_update(*args)
      EssentiaWide.tick
    end
  end
end
