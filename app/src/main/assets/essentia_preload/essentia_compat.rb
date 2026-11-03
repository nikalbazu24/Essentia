# essentia_compat.rb — Essentia (GPLv2, like the rest of Essentia)
#
# Compatibility helpers loaded before every game:
#  * Windows programs (.exe, .bat...) can't run on Android. Games that call them
#    (for example update checkers) get an empty answer instead of a crash.
#  * Essentia.fix_legacy_syntax: rewrites old Ruby 1.8 syntax that modern Ruby
#    rejects. The engine uses it to retry a game script that fails to parse.

begin
  require 'stringio'
rescue LoadError
end

module Essentia
  WINDOWS_PROGRAM = /\.(exe|bat|cmd|com|vbs|ps1|msi)\b/i

  def self.windows_program?(args)
    text = args.flatten.map { |a| a.is_a?(Hash) ? '' : a.to_s }.join(' ')
    !!(text =~ WINDOWS_PROGRAM)
  end

  def self.empty_io
    defined?(StringIO) ? StringIO.new('') : File.open(File::NULL, 'r')
  end

  WHEN_CHUNK = %q{(?:[^:#'"\n?]|::|'[^'\n]*'|"[^"\n]*")+?}

  # Ruby 1.8:  when 0: foo     /   when CONST:       (Ruby 1.9+: "then")
  WHEN_EOL    = /^(\s*when\s+#{WHEN_CHUNK})\s*:(?!:)(?=\s*(?:#.*)?$)/
  WHEN_INLINE = /^(\s*when\s+#{WHEN_CHUNK})\s*:(?!:)\s+(?=\S)/

  def self.fix_legacy_syntax(source)
    s = source.dup
    s.force_encoding('UTF-8')
    s = s.scrub('?') unless s.valid_encoding?
    s = s.gsub(WHEN_EOL) { "#{$1} then" }
    s = s.gsub(WHEN_INLINE) { "#{$1} then " }
    s
  end
end

class << IO
  alias_method :essentia_original_popen, :popen
  def popen(*args, &block)
    if Essentia.windows_program?(args)
      io = Essentia.empty_io
      return block ? block.call(io) : io
    end
    essentia_original_popen(*args, &block)
  end
end

module Kernel
  alias_method :essentia_original_system, :system
  def system(*args)
    return false if Essentia.windows_program?(args)
    essentia_original_system(*args)
  end

  alias_method :essentia_original_backtick, :`
  def `(cmd)
    return '' if Essentia.windows_program?([cmd])
    essentia_original_backtick(cmd)
  end
end
