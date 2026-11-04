# Original minimal Essentials-shaped fixtures; no third-party game scripts.
require 'tmpdir'
ENV['ESSENTIA_LIVE_FILE'] = ''
ENV['ESSENTIA_PROGRESS_FILE'] = ''
module Graphics
  def self.update; end
end
load File.expand_path('../app/src/main/assets/essentia_preload/essentia_live.rb', __dir__)
def check(value, message)
  raise message unless value
end
check(Essentia.live_progress == ['', '', '', ''], 'No trainer must be harmless')
PokemonFixture = Struct.new(:name, :level, :hp, :totalhp)
OldTrainerFixture = Struct.new(:party, :badges, :owned, :seen)
$Trainer = OldTrainerFixture.new([PokemonFixture.new('Pikachu',12,15,30)], [true,false,true], [nil,true,false], [true,true])
old = Essentia.live_progress
check(old[0].include?('Pikachu Nv.12 15/30 PS'), 'Old party values')
check(old[1..3] == [2,1,2], 'Old badges and dex values')
DexFixture = Struct.new(:owned_count, :seen_count)
ModernTrainerFixture = Struct.new(:party, :badge_count, :pokedex)
$player = ModernTrainerFixture.new([],4,DexFixture.new(25,70))
check(Essentia.live_progress[1..3] == [4,25,70], 'Modern player must take precedence')
$player = nil
$Trainer = Object.new
check(Essentia.live_progress == ['', '', '', ''], 'Unsupported trainer must be harmless')
1000.times { Essentia.live_update }
puts 'Live progress: old, modern, absent and unsupported trainer fixtures passed'
