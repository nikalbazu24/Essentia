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

  # Rewrites old Ruby syntax. With the parser's error message, also fixes the exact
  # lines it complains about (the engine calls this again after each failed try).
  def self.fix_legacy_syntax(source, message = nil)
    s = source.dup
    s.force_encoding('UTF-8')
    s = s.scrub('?') unless s.valid_encoding?
    s = s.gsub(WHEN_EOL) { "#{$1} then" }
    s = s.gsub(WHEN_INLINE) { "#{$1} then " }
    s = fix_reported_lines(s, message.to_s) if message
    s
  end

  def self.fix_reported_lines(source, message)
    lines = source.split("\n", -1)
    message.scan(/:(\d+): (Invalid retry|Invalid redo|invalid multibyte escape: (\/.*\/))/) do |num, kind, regex|
      i = num.to_i - 1
      next if i < 0 || i >= lines.size
      line = lines[i]
      case kind
      when 'Invalid retry'
        # Ruby 1.8 allowed "retry" inside loops to start over; the closest
        # modern equivalent is "redo" (repeat the current step).
        lines[i] = line.sub(/\bretry\b/, 'redo')
      when 'Invalid redo'
        lines[i] = line.sub(/\bredo\b/, 'nil')
      else
        # "\x80"-style bytes in a regexp: in UTF-8 they mean characters U+0080...
        if regex && line.include?(regex)
          lines[i] = line.sub(regex) { regex.gsub(/\\x([89a-fA-F][0-9a-fA-F])/) { "\\u00#{$1}" } }
        end
      end
    end
    lines.join("\n")
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
