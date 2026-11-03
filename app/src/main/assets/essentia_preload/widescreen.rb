# widescreen.rb — Essentia (GPLv2, like the rest of Essentia)
#
# Real widescreen for games made for 4:3. The screen is wide all the time
# (no size changes when a battle or menu opens):
#
#   * on the map, the game simply sees a wider screen: more map, player centered
#   * menus, battles, title and other screens made for 4:3 are laid out exactly
#     as designed, in the middle, and their full-width pieces reach the edges:
#       - windows that span the screen (message boxes...) are made wider
#       - full-screen pictures (backgrounds, bars) are extended to the sides:
#         landscapes continue as a mirror image, menu backgrounds and bars
#         continue with their edge colors
#
# Everything here only changes where things are drawn; the game keeps seeing
# its own coordinates (x, width, Graphics.width...).

module EssentiaWide
  @base = nil        # [w, h] the game asked for (its own 4:3 size)
  @depth = 0         # inside a menu/battle opened from the map
  @battle = 0        # inside a battle
  @center = {}       # original camera constants
  @wrapped = {}
  @frame = 0
  @viewports = []    # viewports adjusted for 4:3 screens (weak list)

  class << self
    def aspect
      a = (ENV['ESSENTIA_ASPECT'] || '1.7778').to_f
      a = 16.0 / 9.0 if a < 1.4
      a = 2.2 if a > 2.2
      a
    end

    def base
      @base ||= [Graphics.essentia_raw_width, Graphics.essentia_raw_height]
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

    def wide_w
      wide_size[0]
    end

    # Margin on each side of a 4:3 screen
    def off
      (wide_w - base[0]) / 2
    end

    def active?
      off > 0
    end

    def current_scene
      return SceneManager.scene if defined?(SceneManager) && SceneManager.respond_to?(:scene)
      $scene
    end

    def on_map?
      defined?(Scene_Map) && current_scene.is_a?(Scene_Map)
    end

    def in_battle?
      return true if @battle > 0
      $game_temp && $game_temp.respond_to?(:in_battle) && $game_temp.in_battle
    rescue StandardError
      false
    end

    # Walking on the map: the game sees the wide screen
    def map_mode?
      @depth.zero? && on_map? && !in_battle?
    end

    # A 4:3 screen (menu, battle, title...) being built right now
    def centered?
      active? && !map_mode?
    end

    def inside(battle = false)
      @depth += 1
      @battle += 1 if battle
      yield
    ensure
      @depth -= 1
      @battle -= 1 if battle
    end

    # ------------------------------------------------------------ pictures

    # A copy of a 4:3 full-width picture, extended to the wide screen.
    # Landscapes (battle backgrounds) continue as a mirror image; menu
    # backgrounds and bars repeat their edge colors.
    def extended(src, battle)
      mirror = battle && src.height >= 160
      cache = (src.instance_variable_get(:@essentia_ext) || {})
      key = [wide_w, mirror]
      ext = cache[key]
      return ext if ext && !ext.disposed?
      w = wide_w
      h = src.height
      bw = src.width
      m = off
      ext = Bitmap.new(w, h)
      ext.blt(m, 0, src, Rect.new(0, 0, bw, h))
      if mirror
        m.times do |i|
          ext.blt(m - 1 - i, 0, src, Rect.new([i, bw - 1].min, 0, 1, h))
          ext.blt(m + bw + i, 0, src, Rect.new([bw - 1 - i, 0].max, 0, 1, h))
        end
      else
        ext.stretch_blt(Rect.new(0, 0, m, h), src, Rect.new(0, 0, 1, h))
        ext.stretch_blt(Rect.new(m + bw, 0, w - m - bw, h), src, Rect.new(bw - 1, 0, 1, h))
      end
      cache[key] = ext
      src.instance_variable_set(:@essentia_ext, cache)
      ext
    rescue StandardError
      nil
    end

    # A picture meant to cover the whole 4:3 width
    def full_picture?(bmp)
      bmp && !(bmp.disposed? rescue true) && bmp.width == base[0] &&
        bmp.instance_variable_get(:@essentia_file)
    end

    # ------------------------------------------------------------ viewports

    def adopt_viewport(v)
      r = v.rect
      bw = base[0]
      if r.x == 0 && (r.width == bw || r.width == wide_w)
        # (Viewport.new with no size, in VX Ace, already covers the wide screen)
        v.instance_variable_set(:@essentia_full, true)
        v.essentia_raw_rect = Rect.new(0, r.y, wide_w, r.height)
        gox = v.essentia_raw_ox
        v.instance_variable_set(:@essentia_gox, gox)
        v.essentia_raw_ox = gox - off
      elsif r.x >= 0 && r.x + r.width <= bw
        v.instance_variable_set(:@essentia_shifted, true)
        v.essentia_raw_rect = Rect.new(r.x + off, r.y, r.width, r.height)
      else
        return
      end
      @viewports << v
      @viewports.reject! { |x| x.disposed? rescue true } if @viewports.size > 64
    rescue StandardError
    end

    # Back on the map: viewports made before (map layers created when the
    # game's scripts loaded) behave like map viewports again.
    def release_viewports
      @viewports.each do |v|
        next if (v.disposed? rescue true)
        if v.instance_variable_get(:@essentia_full)
          v.instance_variable_set(:@essentia_full, nil)
          gox = v.instance_variable_get(:@essentia_gox) || 0
          v.instance_variable_set(:@essentia_gox, nil)
          v.essentia_raw_ox = gox
        elsif v.instance_variable_get(:@essentia_shifted)
          v.instance_variable_set(:@essentia_shifted, nil)
          r = v.rect
          v.essentia_raw_rect = Rect.new(r.x - off, r.y, r.width, r.height)
        end
      end
      @viewports.clear
      expand_map_viewports
    rescue StandardError
    end

    # Full-screen 4:3 viewports used by the map grow to the wide size
    def expand_map_viewports
      bw, bh = base
      ObjectSpace.each_object(Viewport) do |v|
        next if (v.disposed? rescue true)
        r = v.rect
        next unless r.x == 0 && r.y == 0 && r.width == bw && r.height == bh
        v.essentia_raw_rect = Rect.new(0, 0, wide_w, bh)
      end
    rescue StandardError
    end

    # ------------------------------------------------------------ camera

    # The player stays centered: camera constants are computed for the 4:3
    # width (4 map units per pixel, half the extra width).
    def fix_camera
      return unless defined?(Game_Player)
      extra = wide_w - base[0]
      [:SCREEN_CENTER_X, :CENTER_X].each do |name|
        here = begin
          Game_Player.const_defined?(name, false)
        rescue ArgumentError # Ruby 1.8
          Game_Player.const_defined?(name)
        end
        next unless here
        @center[name] = Game_Player.const_get(name) unless @center.key?(name)
        value = @center[name] + extra * 2
        next if Game_Player.const_get(name) == value
        Game_Player.send(:remove_const, name)
        Game_Player.const_set(name, value)
      end
    rescue StandardError
    end

    def entering_map
      release_viewports
      fix_camera
    end

    # ------------------------------------------------------------ game hooks

    def install_hooks
      wrap_block_method(:pbFadeOutIn, false)
      wrap_block_method(:pbBattleAnimation, true)
      wrap_init(:Spriteset_Map)
      wrap_init(:TilemapRenderer)
      EssentiaWideWindow.install(SpriteWindow, true) if defined?(SpriteWindow) && SpriteWindow.is_a?(Class)
    end

    def wrap_block_method(name, battle)
      return unless Object.private_method_defined?(name) || Object.method_defined?(name)
      current = Object.instance_method(name)
      return if @wrapped[name] == current
      raw = :"essentia_wide_raw_#{name}"
      Object.send(:alias_method, raw, name)
      Object.send(:define_method, name) do |*args, &block|
        if block
          __send__(raw, *args) { |*a| EssentiaWide.inside(battle) { block.call(*a) } }
        else
          __send__(raw, *args)
        end
      end
      Object.send(:private, name)
      @wrapped[name] = Object.instance_method(name)
    rescue StandardError
    end

    def wrap_init(class_name)
      return unless Object.const_defined?(class_name)
      klass = Object.const_get(class_name)
      return unless klass.is_a?(Class)
      current = klass.instance_method(:initialize)
      return if @wrapped[class_name] == current
      raw = :essentia_wide_raw_initialize
      klass.send(:alias_method, raw, :initialize)
      klass.send(:define_method, :initialize) do |*args, &block|
        EssentiaWide.entering_map if EssentiaWide.depth_zero?
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
      if map_mode? && !@was_map
        entering_map
      end
      @was_map = map_mode?
    end
  end
end

# ---------------------------------------------------------------- Graphics

module Graphics
  class << self
    alias_method :essentia_raw_width, :width
    alias_method :essentia_raw_height, :height
    alias_method :essentia_raw_resize_screen, :resize_screen

    # On the map the game sees the wide screen; 4:3 screens see their own size.
    def width
      EssentiaWide.map_mode? ? essentia_raw_width : EssentiaWide.base[0]
    end

    def resize_screen(width, height)
      EssentiaWide.base = [width, height]
      essentia_raw_resize_screen(*EssentiaWide.wide_size)
    end

    alias_method :essentia_wide_original_update, :update
    def update(*args)
      essentia_wide_original_update(*args)
      EssentiaWide.tick
    end
  end
end

# ---------------------------------------------------------------- Bitmap

class Bitmap
  alias_method :essentia_wide_init, :initialize
  def initialize(*args)
    essentia_wide_init(*args)
    @essentia_file = true if args[0].is_a?(String)
  end
end

# ---------------------------------------------------------------- Viewport

class Viewport
  alias_method :essentia_wide_init, :initialize
  alias_method :essentia_raw_ox, :ox
  alias_method :essentia_raw_ox=, :ox=
  alias_method :essentia_raw_rect=, :rect=

  def initialize(*args)
    essentia_wide_init(*args)
    EssentiaWide.adopt_viewport(self) if EssentiaWide.centered?
  end

  def ox
    @essentia_gox.nil? ? essentia_raw_ox : @essentia_gox
  end

  def ox=(value)
    if @essentia_full
      @essentia_gox = value
      self.essentia_raw_ox = value - EssentiaWide.off
    else
      self.essentia_raw_ox = value
    end
  end

  def rect=(r)
    self.essentia_raw_rect = r
    if @essentia_full || @essentia_shifted
      @essentia_full = @essentia_shifted = nil
      self.essentia_raw_ox = @essentia_gox if @essentia_gox
      @essentia_gox = nil
      EssentiaWide.adopt_viewport(self)
    end
  end
end

# ---------------------------------------------------------------- Sprite

class Sprite
  alias_method :essentia_wide_init, :initialize
  alias_method :essentia_raw_x, :x
  alias_method :essentia_raw_x=, :x=
  alias_method :essentia_raw_bitmap, :bitmap
  alias_method :essentia_raw_bitmap=, :bitmap=
  alias_method :essentia_raw_viewport=, :viewport=

  def initialize(*args)
    essentia_wide_init(*args)
    if EssentiaWide.centered?
      @essentia_screen = true # made for a 4:3 screen
      @essentia_battle = EssentiaWide.in_battle?
      essentia_place if args[0].nil? || args[0].instance_variable_get(:@essentia_full)
    end
  end

  def x
    @essentia_gx.nil? ? essentia_raw_x : @essentia_gx
  end

  def x=(value)
    if @essentia_screen
      @essentia_gx = value
      essentia_place
    else
      self.essentia_raw_x = value
    end
  end

  def bitmap
    @essentia_tracked ? @essentia_gbmp : essentia_raw_bitmap
  end

  def bitmap=(value)
    if @essentia_screen
      @essentia_gbmp = value
      @essentia_tracked = true
      essentia_place
    else
      self.essentia_raw_bitmap = value
    end
  end

  def viewport=(value)
    self.essentia_raw_viewport = value
    essentia_place if @essentia_screen
  end

  # Where (and with which picture) this sprite is really drawn
  def essentia_place
    unless @essentia_tracked
      @essentia_gbmp = essentia_raw_bitmap
      @essentia_tracked = true
    end
    @essentia_gx = essentia_raw_x if @essentia_gx.nil?
    gx = @essentia_gx
    gb = @essentia_gbmp
    vp = viewport
    shift = if vp.nil? then EssentiaWide.off
            elsif vp.instance_variable_get(:@essentia_full) then 0
            else nil
            end
    if shift.nil? # in a viewport that isn't a 4:3 screen: as the game says
      self.essentia_raw_bitmap = gb unless essentia_raw_bitmap.equal?(gb)
      self.essentia_raw_x = gx
      return
    end
    m = EssentiaWide.off
    if gx == 0 && ox == 0 && EssentiaWide.full_picture?(gb) &&
       (ext = EssentiaWide.extended(gb, @essentia_battle))
      self.essentia_raw_bitmap = ext unless essentia_raw_bitmap.equal?(ext)
      self.essentia_raw_x = shift - m
    elsif gx == 0 && ox == 0 && gb && !(gb.disposed? rescue true) && gb.width >= EssentiaWide.wide_w
      # already a wide picture (a snapshot of the screen)
      self.essentia_raw_bitmap = gb unless essentia_raw_bitmap.equal?(gb)
      self.essentia_raw_x = shift - m
    else
      self.essentia_raw_bitmap = gb unless essentia_raw_bitmap.equal?(gb)
      self.essentia_raw_x = gx + shift
    end
  rescue StandardError
  end
end

# ---------------------------------------------------------------- Plane

class Plane
  alias_method :essentia_wide_init, :initialize
  alias_method :essentia_raw_bitmap, :bitmap
  alias_method :essentia_raw_bitmap=, :bitmap=
  alias_method :essentia_raw_ox, :ox
  alias_method :essentia_raw_ox=, :ox=

  def initialize(*args)
    essentia_wide_init(*args)
    vp = args[0]
    if EssentiaWide.centered? && (vp.nil? || vp.instance_variable_get(:@essentia_full))
      @essentia_screen = true
      @essentia_battle = EssentiaWide.in_battle?
      @essentia_moves = 0
    end
  end

  def bitmap
    @essentia_tracked ? @essentia_gbmp : essentia_raw_bitmap
  end

  def bitmap=(value)
    return self.essentia_raw_bitmap = value unless @essentia_screen
    @essentia_gbmp = value
    @essentia_tracked = true
    essentia_place
  end

  def ox
    @essentia_gox.nil? ? essentia_raw_ox : @essentia_gox
  end

  def ox=(value)
    return self.essentia_raw_ox = value unless @essentia_screen
    @essentia_moves += 1 if @essentia_gox && value != @essentia_gox
    @essentia_gox = value
    essentia_place
  end

  # Still backgrounds are extended; scrolling patterns keep tiling.
  def essentia_place
    gb = @essentia_tracked ? @essentia_gbmp : essentia_raw_bitmap
    gox = @essentia_gox || 0
    in_view = !viewport.nil?
    if @essentia_moves < 3 && EssentiaWide.full_picture?(gb) &&
       (ext = EssentiaWide.extended(gb, @essentia_battle))
      self.essentia_raw_bitmap = ext unless essentia_raw_bitmap.equal?(ext)
      # inside a moved viewport the picture follows its offset: undo it
      self.essentia_raw_ox = gox + (in_view ? EssentiaWide.off : 0)
    else
      self.essentia_raw_bitmap = gb unless essentia_raw_bitmap.equal?(gb)
      self.essentia_raw_ox = gox - (in_view ? 0 : EssentiaWide.off)
    end
  rescue StandardError
  end
end

# ---------------------------------------------------------------- windows

# Windows of 4:3 screens: the ones that span the whole width get wider, the
# others stay where the game puts them (in the middle part).
module EssentiaWideWindow
  def self.install(klass, sprite_window)
    return if klass.instance_variable_get(:@essentia_wide_window)
    klass.instance_variable_set(:@essentia_wide_window, true)
    klass.class_eval do
      alias_method :essentia_wwin_init, :initialize
      alias_method :essentia_raw_x, :x
      alias_method :essentia_raw_x=, :x=
      alias_method :essentia_raw_width, :width
      alias_method :essentia_raw_width=, :width=
      alias_method :essentia_raw_viewport=, :viewport= if method_defined?(:viewport=)

      define_method(:initialize) do |*args, &block|
        @essentia_sprite_window = true if sprite_window
        @essentia_init_depth = (@essentia_init_depth || 0) + 1
        begin
          essentia_wwin_init(*args, &block)
        ensure
          @essentia_init_depth -= 1
        end
        # only once the whole window is built (subclasses call super)
        if @essentia_init_depth.zero? && EssentiaWide.centered?
          @essentia_screen = true
          essentia_place
        end
      end

      def x
        @essentia_gx.nil? ? essentia_raw_x : @essentia_gx
      end

      def x=(value)
        return self.essentia_raw_x = value unless @essentia_screen
        @essentia_gx = value
        essentia_place
      end

      def width
        @essentia_gw.nil? ? essentia_raw_width : @essentia_gw
      end

      def width=(value)
        return self.essentia_raw_width = value unless @essentia_screen
        @essentia_gw = value
        essentia_place
      end

      if method_defined?(:essentia_raw_viewport=)
        def viewport=(value)
          self.essentia_raw_viewport = value
          essentia_place if @essentia_screen
        end
      end

      def essentia_place
        @essentia_gx = essentia_raw_x if @essentia_gx.nil?
        @essentia_gw = essentia_raw_width if @essentia_gw.nil?
        vp = viewport
        if vp.nil?
          # Essentia's sprite-based windows move their own pieces (which are
          # already shifted); engine windows are shifted here.
          shift = @essentia_sprite_window ? 0 : EssentiaWide.off
          full_shift = @essentia_sprite_window ? -EssentiaWide.off : 0
        elsif vp.instance_variable_get(:@essentia_full)
          shift = 0
          full_shift = -EssentiaWide.off
        else
          self.essentia_raw_width = @essentia_gw
          self.essentia_raw_x = @essentia_gx
          return
        end
        if @essentia_gx == 0 && @essentia_gw == EssentiaWide.base[0]
          self.essentia_raw_width = EssentiaWide.wide_w
          self.essentia_raw_x = full_shift
        else
          self.essentia_raw_width = @essentia_gw
          self.essentia_raw_x = @essentia_gx + shift
        end
      rescue StandardError
      end
    end
  end
end

EssentiaWideWindow.install(Window, false)

# The game's own size is what it had before Essentia widened the screen
EssentiaWide.base
begin
  Graphics.essentia_raw_resize_screen(*EssentiaWide.wide_size)
rescue StandardError
end
