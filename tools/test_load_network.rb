# Original fixture of the auto-check API shape; no game scripts are executed.
ENV['ESSENTIA_ACK_FILE'] = ''
module Graphics
  def self.width; 640; end
  def self.height; 480; end
  def self.update; end
  def self.resize_screen(*args); end
  def self.snap_to_bitmap; nil; end
end
load File.expand_path('../app/src/main/assets/essentia_preload/essentia_games.rb', __dir__)
$automatic_requests = 0
def pbCheckVersionOnLoad
  $automatic_requests += 1
  raise 'The load menu attempted a blocking network request'
end
pbCheckVersionOnLoad
raise 'Automatic request was not suppressed' unless $automatic_requests == 0
# Redefinition later in a game's scripts must remain safe.
def pbCheckVersionOnLoad
  $automatic_requests += 1
end
pbCheckVersionOnLoad
raise 'Redefined auto-check escaped the guard' unless $automatic_requests == 0
def manual_online_feature
  :manual_still_available
end
raise 'Manual online features changed' unless manual_online_feature == :manual_still_available
puts 'Load-menu network guard: initial and redefined automatic checks skipped; unrelated features preserved'
