require 'tmpdir'
Dir.mktmpdir('essentia-diagnostics') do |dir|
  ENV['ESSENTIA_DIAGNOSTICS_FILE']=File.join(dir,'sample.tsv')
  module System
    def self.uptime; @time || 0.0; end
    def self.advance; @time=uptime+1.0/60; end
  end
  module Graphics
    def self.update; System.advance; end
  end
  load File.expand_path('../app/src/main/assets/essentia_preload/essentia_diagnostics.rb',__dir__)
  125.times { Graphics.update }
  fields=File.read(ENV['ESSENTIA_DIAGNOSTICS_FILE']).split("\t")
  raise 'FPS timing is wrong' unless (fields[0].to_f-60).abs<0.2
  raise 'Frame interval is wrong' unless (fields[1].to_f-16.7).abs<0.2
  raise 'Temporary file not finalized' if File.exist?(ENV['ESSENTIA_DIAGNOSTICS_FILE']+'.tmp')
end
puts 'Diagnostics: measured frame interval and FPS persisted atomically'
