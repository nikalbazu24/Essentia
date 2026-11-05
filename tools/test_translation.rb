require 'tmpdir'

module Graphics
  def self.update
  end
end

Dir.mktmpdir do |root|
  ENV['ESSENTIA_TR_FILE'] = File.join(root, 'pt.tsv')
  ENV['ESSENTIA_TR_REQ'] = root
  File.write(ENV['ESSENTIA_TR_FILE'], "Hello\tOlá\n")
  require_relative '../app/src/main/assets/essentia_preload/essentia_translate'

  def pbMessage(text)
    yield if block_given?
    text
  end
  called = false
  raise 'first dialogue was not translated' unless pbMessage('Hello') { called = true } == 'Olá' && called

  # A game may alias and redefine a message hook after loading its scripts.
  alias translation_fixture_original pbMessage
  def pbMessage(text, &block)
    translation_fixture_original(text, &block)
  end
  raise 'message wrapper recursed or lost cached text' unless pbMessage('Hello') == 'Olá'

  original = "ポケモン"
  EssentiaTranslate.lookup(original)
  EssentiaTranslate.flush
  file = Dir.glob(File.join(root, '*.req')).first
  raise 'non-Latin dialogue was not queued' unless file && File.read(file).include?(original)

  EssentiaTranslate.lookup(original)
  EssentiaTranslate.flush
  raise 'duplicate was queued' unless Dir.glob(File.join(root, '*.req')).size == 1

  File.open(ENV['ESSENTIA_TR_FILE'], 'ab') { |out| out.write("#{original}\tPokémon\n") }
  EssentiaTranslate.load_new
  raise 'background result not used' unless EssentiaTranslate.lookup(original) == 'Pokémon'
end

puts 'Translation dialogue, hook redefinition, queue and cache passed'
