# Minimal RGSS doubles to exercise the actual preload, without game assets.
module Graphics
  @width = 512
  @height = 384

  class << self
    attr_reader :width, :height

    def resize_screen(width, height)
      @width, @height = width, height
    end

    def update
    end
  end
end

Rect = Struct.new(:x, :y, :width, :height)

class Bitmap; end
class Sprite
  attr_accessor :x, :y, :bitmap, :viewport, :src_rect
end
class Plane
  attr_accessor :bitmap, :ox, :viewport, :z
end
class Viewport
  attr_accessor :rect, :ox

  def initialize(x, y, width, height)
    @rect = Rect.new(x, y, width, height)
    @ox = 0
  end
end
class Window
  attr_accessor :x, :width, :viewport

  def initialize
    @x, @width = 0, 512
  end
end
class Game_Player
  CENTER_X = 1024
end

class Spriteset_Map
  attr_reader :width_during_init, :result

  def initialize(value, offset = 0)
    @width_during_init = Graphics.width
    @result = yield(value + offset)
  end
end

def pbBattleAnimation(value)
  yield(value)
end

ENV['ESSENTIA_ASPECT'] = '1.7778'
load File.expand_path('../app/src/main/assets/essentia_preload/widescreen.rb', File.dirname(__FILE__))
EssentiaWide.install_hooks

class Spriteset_Map
  alias_method :plugin_map_init, :initialize

  def initialize(value, offset = 0, &block)
    plugin_map_init(value + 1, offset, &block)
  end
end

class TilemapRenderer < Spriteset_Map; end

alias plugin_battle_animation pbBattleAnimation
def pbBattleAnimation(value, &block)
  plugin_battle_animation(value + 1, &block)
end

EssentiaWide.install_hooks
map_hook = Spriteset_Map.instance_method(:initialize)
battle_hook = Object.instance_method(:pbBattleAnimation)
EssentiaWide.install_hooks
raise 'reinstalled an unchanged map hook' unless Spriteset_Map.instance_method(:initialize) == map_hook
raise 'reinstalled an unchanged battle hook' unless Object.instance_method(:pbBattleAnimation) == battle_hook
raise 'battle hook visibility changed' unless Object.private_method_defined?(:pbBattleAnimation)

Graphics.update
raise 'original resolution leaked before map construction' unless Graphics.width == 512
window = Window.new
raise 'menu no longer has its original dimensions' unless window.essentia_cw_raw_width == 512
raise 'menu is not centered' unless window.essentia_cw_raw_x == 86

[Spriteset_Map, TilemapRenderer].each do |klass|
  map = klass.new(3, 2) { |number| number * 2 }
  raise 'map hook lost arguments or block' unless map.result == 12
  raise 'map hook lost wide dimensions' unless map.width_during_init == 684
  raise 'map mode leaked after initialization' unless Graphics.width == 512
end
expected_camera = ENV['ESSENTIA_WIDE_CAMERA'] == '10' ? 1437 : 1368
raise 'camera offset not applied' unless Game_Player::CENTER_X == expected_camera

result = pbBattleAnimation(3) do |value|
  raise 'battle context missing' unless EssentiaWide.in_battle?
  viewport = Viewport.new(0, 0, 512, 384).essentia_rect
  enabled = ENV['ESSENTIA_SCREEN_MODE'] == 'wide' && ENV['ESSENTIA_WIDE_BATTLES'] == '1'
  expected = enabled ? [0, 684] : [86, 512]
  raise 'battle viewport changed' unless [viewport.x, viewport.width] == expected
  if ENV['ESSENTIA_WIDE_SPREAD'] == '0'
    raise 'battle separation not disabled' unless EssentiaWide.spread(512, 0) == 0
  end
  value * 2
end
raise 'battle hook lost return value' unless result == 8
raise 'battle context leaked' if EssentiaWide.in_battle? || !EssentiaWide.depth_zero?

puts "Widescreen #{ENV['ESSENTIA_SCREEN_MODE']}: alias redefinition, inherited initializer and battle viewport passed"
