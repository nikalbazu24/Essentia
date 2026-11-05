# Original fixtures only. No third-party game code or saved-game deserialization.
ENV['ESSENTIA_CMD_FILE']='';ENV['ESSENTIA_ACK_FILE']=''
module Graphics
  def self.update; end
end
load File.expand_path('../app/src/main/assets/essentia_preload/essentia_actions.rb',__dir__)
def check(value,message); raise message unless value; end
check(EssentiaActions.companion_actions==%w[shot speed],'Unknown game gets generic utilities')
class Scene_Map
  def update; end
end
EssentiaActions.install_hooks
$scene=Scene_Map.new
$game_temp=Struct.new(:in_battle,:in_menu,:message_window_showing,:player_transferring).new(false,false,false,false)
$game_player=Object.new
check(EssentiaActions.companion_context=='map','Recognized idle map')
check(EssentiaActions.companion_actions==%w[shot speed],'No invented Pokemon features')
class PokemonParty_Scene; end
class PokemonPartyScreen; end
class PokemonBag_Scene; end
class PokemonBagScreen; end
class PokemonPokedex_Scene; end
class PokemonPokedexScreen; end
$Trainer=Struct.new(:party,:has_pokedex).new([],false)
$bag=Object.new
module Game
  def self.save; raise 'Capability probing must never save'; end
end
$game_system=Struct.new(:save_disabled).new(true)
cap=EssentiaActions.companion_actions
check(cap.include?('party') && cap.include?('bag'),'Recognized menu APIs')
check(!cap.include?('dex') && !cap.include?('save'),'Locked dex and disabled saves stay hidden')
$Trainer.has_pokedex=true;$game_system.save_disabled=false
cap=EssentiaActions.companion_actions
check(cap.include?('dex') && cap.include?('save'),'Unlocked APIs advertised without invoking them')
$game_temp.in_battle=true
check(EssentiaActions.companion_context=='battle','Battle context')
check(EssentiaActions.companion_actions==cap,'Stable capabilities during battle; UI gates unsafe actions')
$game_temp.in_battle=false;$game_temp.in_menu=true
check(EssentiaActions.companion_context=='busy','Menu context')
puts 'Companion: unknown and supported games, locks, map/battle/menu states and read-only probes passed'
