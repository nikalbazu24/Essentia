# widescreen.rb — Essentia (GPLv2, like the rest of Essentia)
#
# Real widescreen for games made for 4:3. The screen is wide all the time
# (no size changes when a battle or menu opens):
#
#   * on the map, the game simply sees a wider screen: more map, player centered
#   * battles get a wider field: the Pokémon and their boxes spread to the
#     sides, the battle background is extended to the edges
#   * title, menus and other screens made for 4:3 stay exactly as designed, in
#     the middle (nothing that should be hidden shows up); their backgrounds
#     reach the edges on a layer just behind them:
#       - full-screen pictures are extended (the outer strip is widened a
#         little; bars continue with their edge colors)
#       - scrolling patterns keep tiling across the whole width
#       - windows that span the screen (message boxes...) are made wider
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
  @building = 0      # making the map's pieces

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

    # Pokémon Essentials: only the battle itself (inside pbBattleAnimation), not
    # its intro animation, which still runs over the map (its transitions must
    # cover the whole wide screen and the map must keep drawing all of it).
    def in_battle?
      return true if @battle > 0
      return false if @wrapped[:pbBattleAnimation]
      $game_temp && $game_temp.respond_to?(:in_battle) && $game_temp.in_battle
    rescue StandardError
      false
    end

    # Walking on the map: the game sees the wide screen.
    # (Asked very often, e.g. through Graphics.width, so it's remembered for the
    # frame and recalculated when a menu or battle opens or closes.)
    def map_mode?
      return true if @building > 0
      @mm = (@depth.zero? && on_map? && !in_battle? ? true : false) if @mm.nil?
      @mm
    end

    # The map's pieces (tilemap, map viewports) are being made: they must be made
    # for the wide screen even if this happens behind a fade or a menu, or the
    # map would be drawn only up to the 4:3 width (black side, missing tiles).
    def building_map
      @building += 1
      @mm = nil
      yield
    ensure
      @building -= 1
      @mm = nil
    end

    def forget_mode
      @mm = nil
    end

    # A 4:3 screen (menu, battle, title...) being built right now
    def centered?
      active? && !map_mode?
    end

    def inside(battle = false)
      @depth += 1
      @battle += 1 if battle
      @mm = nil
      yield
    ensure
      @depth -= 1
      @battle -= 1 if battle
      @mm = nil
    end

    # Battles get more room: the field (Pokémon, bases, data boxes) spreads
    # towards the sides; the bottom menus stay as designed. Things near the
    # middle move gradually, so nothing jumps when crossing it.
    def spread(cx, cy)
      return 0 if cy > base[1] * 0.74
      t = (cx - base[0] / 2.0) / 64.0
      t = -1.0 if t < -1.0
      t = 1.0 if t > 1.0
      (off * 0.55 * t).round
    end

    # ------------------------------------------------------------ pictures

    # A copy of a 4:3 full-width picture, extended to the wide screen.
    # Backgrounds get their outer strip widened; bars repeat their edge colors.
    # Made once per picture and remembered.
    def extended(src, battle)
      tall = src.height >= base[1] / 2
      cache = (src.instance_variable_get(:@essentia_ext) || {})
      key = [wide_w, tall]
      ext = cache[key]
      return ext if ext && !ext.disposed?
      h = src.height
      bw = src.width
      m = off
      w = bw + 2 * m   # pictures a bit wider than the screen (scrolling) too
      ext = Bitmap.new(w, h)
      if tall
        # Backgrounds: the outer strip of the picture is widened a little to
        # reach the edge (no fold, no seam); the middle stays exactly as drawn.
        k = [m * 2, bw / 3].min
        ext.stretch_blt(Rect.new(0, 0, m + k, h), src, Rect.new(0, 0, k, h))
        ext.blt(m + k, 0, src, Rect.new(k, 0, bw - 2 * k, h))
        ext.stretch_blt(Rect.new(m + bw - k, 0, w - (m + bw - k), h), src, Rect.new(bw - k, 0, k, h))
      else
        # bars and strips: their edge colors continue
        ext.blt(m, 0, src, Rect.new(0, 0, bw, h))
        ext.stretch_blt(Rect.new(0, 0, m, h), src, Rect.new(0, 0, 1, h))
        ext.stretch_blt(Rect.new(m + bw, 0, w - m - bw, h), src, Rect.new(bw - 1, 0, 1, h))
      end
      cache[key] = ext
      src.instance_variable_set(:@essentia_ext, cache)
      ext
    rescue StandardError
      nil
    end

    # A picture meant to cover the whole 4:3 width: loaded from a file, or
    # solid at its left and right edges (drawn layers for text are transparent
    # there). Decided once per picture.
    def full_picture?(bmp)
      return false unless bmp && !(bmp.disposed? rescue true)
      return false if bmp.width < base[0] || bmp.width >= wide_w   # (wider: a screenshot)
      return true if bmp.instance_variable_get(:@essentia_file)
      known = bmp.instance_variable_get(:@essentia_solid)
      return known unless known.nil?
      solid = bmp.height >= 16 && solid_edges?(bmp)
      bmp.instance_variable_set(:@essentia_solid, solid)
      solid
    end

    def solid_edges?(bmp)
      w = bmp.width
      h = bmp.height
      [0.1, 0.5, 0.9].each do |f|
        y = (h * f).to_i
        return false if bmp.get_pixel(0, y).alpha < 250 || bmp.get_pixel(w - 1, y).alpha < 250
      end
      true
    rescue StandardError
      false
    end

    # ------------------------------------------------------------ viewports

    def adopt_viewport(v)
      r = v.essentia_rect
      bw = base[0]
      return if @making_backdrop
      if r.x == 0 && (r.width == bw || r.width == wide_w) && !in_battle?
        # Title, menus, bag...: the screen stays exactly as designed, in the
        # middle (nothing hidden off-screen shows up); only its background
        # pictures reach the edges, on a layer just behind (see backdrop_for).
        v.instance_variable_set(:@essentia_cfull, true)
        v.essentia_raw_rect = Rect.new(off, r.y, bw, r.height)
      elsif r.x == 0 && (r.width == bw || r.width == wide_w)
        # Battles: the wide field (things spread to the sides)
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
        elsif v.instance_variable_get(:@essentia_shifted) || v.instance_variable_get(:@essentia_cfull)
          v.instance_variable_set(:@essentia_shifted, nil)
          v.instance_variable_set(:@essentia_cfull, nil)
          r = v.essentia_rect
          v.essentia_raw_rect = Rect.new(r.x - off, r.y, r.width, r.height)
        end
      end
      @viewports.clear
      expand_map_viewports
    rescue StandardError
    end

    # A wide layer just behind a 4:3 screen, for its background pictures.
    def backdrop_for(v)
      b = v.instance_variable_get(:@essentia_back)
      return b if b && !(b.disposed? rescue true)
      r = v.essentia_rect
      @making_backdrop = true
      b = Viewport.new(0, r.y, wide_w, r.height)
      @making_backdrop = false
      b.z = v.z - 1
      v.instance_variable_set(:@essentia_back, b)
      (@backdrops ||= []) << [v, b]
      b
    ensure
      @making_backdrop = false
    end

    # The backdrop follows its screen (shown/hidden, fades, colors, gone).
    def sync_backdrops
      return unless @backdrops && !@backdrops.empty?
      @backdrops.reject! do |v, b|
        if (b.disposed? rescue true)
          true
        elsif (v.disposed? rescue true)
          b.dispose rescue nil
          true
        else
          begin
            b.visible = v.visible
            b.color = v.color
            b.tone = v.tone
            b.z = v.z - 1
            r = v.essentia_rect
            br = b.essentia_rect
            b.essentia_raw_rect = Rect.new(0, r.y, wide_w, r.height) if br.y != r.y || br.height != r.height
          rescue StandardError
          end
          false
        end
      end
    end

    # Full-screen 4:3 viewports used by the map grow to the wide size
    def expand_map_viewports
      bw, bh = base
      ObjectSpace.each_object(Viewport) do |v|
        next if (v.disposed? rescue true)
        r = v.essentia_rect
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
        # the map is being built: it must see the wide screen
        EssentiaWide.building_map do
          EssentiaWide.entering_map if EssentiaWide.depth_zero?
          EssentiaWide.expand_map_viewports
          __send__(raw, *args, &block)
        end
      end
      klass.send(:private, :initialize)
      @wrapped[class_name] = klass.instance_method(:initialize)
    rescue StandardError
    end

    # Old Pokémon Essentials replaces Graphics.width with its own (always 4:3)
    def keep_width
      meta = class << Graphics; self; end
      return if @my_width && meta.instance_method(:width) == @my_width
      meta.send(:define_method, :width) { essentia_wide_width }
      @my_width = meta.instance_method(:width)
    rescue StandardError
    end

    def depth_zero?
      @depth.zero?
    end

    def tick
      @frame += 1
      @mm = nil
      if @frame == 1
        # widened here (not while loading) so the window is ready
        w, h = wide_size
        Graphics.essentia_raw_resize_screen(w, h) if Graphics.essentia_raw_width != w
      end
      install_hooks if @frame == 1 || (@frame % 120).zero?
      keep_width if @frame <= 2 || (@frame % 30).zero?
      sync_backdrops
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
    def essentia_wide_width
      EssentiaWide.map_mode? ? essentia_raw_width : EssentiaWide.base[0]
    end

    def width
      essentia_wide_width
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
  alias_method :essentia_rect, :rect

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
    if @essentia_full || @essentia_shifted || @essentia_cfull
      @essentia_full = @essentia_shifted = @essentia_cfull = nil
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
  alias_method :essentia_raw_viewport, :viewport

  def initialize(*args)
    essentia_wide_init(*args)
    if EssentiaWide.centered?
      @essentia_screen = true # made for a 4:3 screen
      @essentia_battle = EssentiaWide.in_battle?
      vp = args[0]
      essentia_place if vp.nil? || vp.instance_variable_get(:@essentia_full) ||
                        vp.instance_variable_get(:@essentia_cfull)
    end
  end

  # The game always sees its own viewport (a background picture may really be
  # drawn on the wide layer behind it).
  def viewport
    @essentia_moved ? @essentia_gvp : essentia_raw_viewport
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
    @essentia_moved = false
    @essentia_gvp = nil
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
    if vp && vp.instance_variable_get(:@essentia_cfull)
      # 4:3 screen: everything as designed; a background picture goes, extended,
      # to the wide layer behind
      if ox == 0 && gx <= 0 && EssentiaWide.full_picture?(gb) && gx + gb.width >= EssentiaWide.base[0] &&
         (ext = EssentiaWide.extended(gb, false))
        back = EssentiaWide.backdrop_for(vp)
        unless @essentia_moved
          @essentia_gvp = vp
          @essentia_moved = true
          self.essentia_raw_viewport = back
        end
        self.essentia_raw_bitmap = ext unless essentia_raw_bitmap.equal?(ext)
        self.essentia_raw_x = gx
        essentia_full_src(ext)
      else
        if @essentia_moved
          @essentia_moved = false
          self.essentia_raw_viewport = vp
        end
        self.essentia_raw_bitmap = gb unless essentia_raw_bitmap.equal?(gb)
        self.essentia_raw_x = gx
      end
      return
    end
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
    if ox == 0 && gx <= 0 && EssentiaWide.full_picture?(gb) && gx + gb.width >= EssentiaWide.base[0] &&
       (ext = EssentiaWide.extended(gb, @essentia_battle))
      self.essentia_raw_bitmap = ext unless essentia_raw_bitmap.equal?(ext)
      self.essentia_raw_x = gx + shift - m
      essentia_full_src(ext)
    elsif gx == 0 && ox == 0 && gb && !(gb.disposed? rescue true) && gb.width >= EssentiaWide.wide_w
      # already a wide picture (a snapshot of the screen)
      self.essentia_raw_bitmap = gb unless essentia_raw_bitmap.equal?(gb)
      self.essentia_raw_x = shift - m
    else
      self.essentia_raw_bitmap = gb unless essentia_raw_bitmap.equal?(gb)
      extra = 0
      if @essentia_battle && gb
        r = src_rect
        cx = gx - ox + r.width * zoom_x / 2.0
        cy = y - oy + r.height * zoom_y / 2.0
        extra = EssentiaWide.spread(cx, cy)
      end
      self.essentia_raw_x = gx + shift + extra
    end
  rescue StandardError
  end

  # A sprite showing an extended picture shows all of it (the game may have
  # set a source rectangle for the original picture).
  def essentia_full_src(ext)
    r = src_rect
    gb = @essentia_gbmp
    if r && gb && r.x == 0 && r.y == 0 && (r.width == gb.width || r.width == 0 || r.width == ext.width)
      r.set(0, 0, ext.width, ext.height) if r.width != ext.width || r.height != ext.height
    end
  rescue StandardError
  end

  alias_method :essentia_raw_src_rect=, :src_rect=
  def src_rect=(r)
    self.essentia_raw_src_rect = r
    ext = essentia_raw_bitmap
    essentia_full_src(ext) if @essentia_screen && ext && @essentia_gbmp && !ext.equal?(@essentia_gbmp)
  end

  alias_method :essentia_raw_y=, :y=
  def y=(value)
    self.essentia_raw_y = value
    essentia_place if @essentia_battle
  end
end

# ---------------------------------------------------------------- Plane

class Plane
  alias_method :essentia_wide_init, :initialize
  alias_method :essentia_raw_bitmap, :bitmap
  alias_method :essentia_raw_bitmap=, :bitmap=
  alias_method :essentia_raw_ox, :ox
  alias_method :essentia_raw_ox=, :ox=
  alias_method :essentia_raw_viewport, :viewport
  alias_method :essentia_raw_viewport=, :viewport=
  alias_method :essentia_raw_z=, :z=

  def initialize(*args)
    essentia_wide_init(*args)
    vp = args[0]
    if EssentiaWide.centered? && (vp.nil? || vp.instance_variable_get(:@essentia_full) ||
                                  vp.instance_variable_get(:@essentia_cfull))
      @essentia_screen = true
      @essentia_battle = EssentiaWide.in_battle?
      @essentia_moves = 0
    end
  end

  # the game always sees its own viewport
  def viewport
    @essentia_moved ? @essentia_gvp : essentia_raw_viewport
  end

  def viewport=(value)
    if @essentia_moved
      @essentia_moved = false
      @essentia_gvp = nil
    end
    self.essentia_raw_viewport = value
    essentia_place if @essentia_screen
  end

  def z=(value)
    self.essentia_raw_z = value
    essentia_place if @essentia_screen
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
    vp = viewport
    if vp && vp.instance_variable_get(:@essentia_cfull)
      # 4:3 screen: background planes (the lowest ones) cover the whole width
      # on the layer behind it; anything else stays inside the screen
      if z <= 0 && gb && !(gb.disposed? rescue true)
        back = EssentiaWide.backdrop_for(vp)
        unless @essentia_moved
          @essentia_gvp = vp
          @essentia_moved = true
          self.essentia_raw_viewport = back
        end
        if @essentia_moves < 3 && EssentiaWide.full_picture?(gb) &&
           (ext = EssentiaWide.extended(gb, false))
          self.essentia_raw_bitmap = ext unless essentia_raw_bitmap.equal?(ext)
          self.essentia_raw_ox = gox
        else
          self.essentia_raw_bitmap = gb unless essentia_raw_bitmap.equal?(gb)
          self.essentia_raw_ox = gox - EssentiaWide.off
        end
      else
        if @essentia_moved
          @essentia_moved = false
          self.essentia_raw_viewport = vp
        end
        self.essentia_raw_bitmap = gb unless essentia_raw_bitmap.equal?(gb)
        self.essentia_raw_ox = gox
      end
      return
    end
    in_view = !vp.nil?
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
# The engine's Window and Pokémon Essentials' SpriteWindow (a subclass made of
# sprites) are wrapped separately, each with its own method names, so one
# never calls into the other by mistake.
module EssentiaWideWindow
  TEMPLATE = <<-'RUBY'
    alias_method :essentia_P_init, :initialize
    alias_method :essentia_P_raw_x, :x
    alias_method :essentia_P_raw_x=, :x=
    alias_method :essentia_P_raw_width, :width
    alias_method :essentia_P_raw_width=, :width=
    alias_method :essentia_P_raw_viewport=, :viewport=

    def initialize(*args, &block)
      @essentia_init_depth = (@essentia_init_depth || 0) + 1
      begin
        essentia_P_init(*args, &block)
      ensure
        @essentia_init_depth -= 1
      end
      # only once the whole window is built (subclasses call super)
      if @essentia_init_depth.zero? && EssentiaWide.centered?
        @essentia_P_on = true
        essentia_P_place
      end
    end

    def x
      @essentia_P_on ? @essentia_gx : essentia_P_raw_x
    end

    def x=(value)
      return self.essentia_P_raw_x = value unless @essentia_P_on
      @essentia_gx = value
      essentia_P_place
    end

    def width
      @essentia_P_on ? @essentia_gw : essentia_P_raw_width
    end

    def width=(value)
      return self.essentia_P_raw_width = value unless @essentia_P_on
      @essentia_gw = value
      essentia_P_place
    end

    def viewport=(value)
      self.essentia_P_raw_viewport = value
      essentia_P_place if @essentia_P_on
    end

    def essentia_P_place
      @essentia_gx = essentia_P_raw_x if @essentia_gx.nil?
      @essentia_gw = essentia_P_raw_width if @essentia_gw.nil?
      vp = viewport
      if vp.nil?
        # sprite-based windows move their own pieces (already shifted);
        # engine windows are shifted here
        shift = SPRITES ? 0 : EssentiaWide.off
        full_x = SPRITES ? -EssentiaWide.off : 0
      elsif vp.instance_variable_get(:@essentia_full)
        shift = 0
        full_x = -EssentiaWide.off
      else
        self.essentia_P_raw_width = @essentia_gw
        self.essentia_P_raw_x = @essentia_gx
        return
      end
      if @essentia_gx == 0 && @essentia_gw == EssentiaWide.base[0]
        self.essentia_P_raw_width = EssentiaWide.wide_w
        self.essentia_P_raw_x = full_x
      else
        self.essentia_P_raw_width = @essentia_gw
        self.essentia_P_raw_x = @essentia_gx + shift
      end
    rescue Exception
      @essentia_P_on = false
    end
  RUBY

  def self.install(klass, sprite_window)
    return if klass.instance_variable_get(:@essentia_wide_window)
    prefix = sprite_window ? 'sw' : 'cw'
    needed = [:initialize, :x, :x=, :width, :width=, :viewport=]
    if sprite_window
      # only if the class really implements these itself
      own = klass.instance_methods(false).map { |m| m.to_sym } + klass.private_instance_methods(false).map { |m| m.to_sym }
      return unless needed.all? { |m| own.include?(m) }
    else
      return unless needed.all? { |m| klass.method_defined?(m) || klass.private_method_defined?(m) }
    end
    klass.instance_variable_set(:@essentia_wide_window, true)
    code = TEMPLATE.gsub('essentia_P_', "essentia_#{prefix}_").gsub('SPRITES', sprite_window ? 'true' : 'false')
    klass.class_eval(code)
  rescue Exception
  end
end

EssentiaWideWindow.install(Window, false)

# The game's own size is what it has before Essentia widens the screen
EssentiaWide.base
