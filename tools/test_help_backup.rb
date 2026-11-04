require 'tmpdir'
module Graphics
  class << self
    attr_accessor :frame_rate
    def update; end
  end
end
load File.expand_path('../app/src/main/assets/essentia_preload/essentia_actions.rb', __dir__)
Dir.mktmpdir('essentia-help') do |dir|
  saves = File.join(dir,'saves');Dir.mkdir(saves)
  backup = File.join(dir,'antes-ayuda')
  ENV['ESSENTIA_DATA_DIR'] = saves
  ENV['ESSENTIA_HELP_BACKUP_DIR'] = backup
  File.open(File.join(saves,'Game.rxdata'),'wb') { |f| f.write('original save') }
  File.open(File.join(saves,'Scripts.rxdata'),'wb') { |f| f.write('game scripts') }
  raise 'backup failed' unless EssentiaActions.backup_before_help
  raise 'save bytes changed' unless File.read(File.join(backup,'Game.rxdata')) == 'original save'
  raise 'game scripts must not be treated as saves' if File.exist?(File.join(backup,'Scripts.rxdata'))
  File.open(File.join(saves,'Game.rxdata'),'wb') { |f| f.write('new save') }
  raise 'session backup should stay original' unless EssentiaActions.backup_before_help
  raise 'backup overwritten' unless File.read(File.join(backup,'Game.rxdata')) == 'original save'
end
puts 'Optional-help backup: original save preserved; scripts excluded; repeated use does not overwrite'
