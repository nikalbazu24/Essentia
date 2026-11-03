# essentia_ruby18.rb — Essentia (GPLv2, like the rest of Essentia)
#
# Only for the classic engine (Ruby 1.8.7). The bundled helper scripts were
# written for modern Ruby; these few methods let them run on Ruby 1.8 too.
# Nothing here changes how the game's own Ruby 1.8 code behaves.

class String
  def getbyte(i); self[i]; end unless method_defined?(:getbyte)
  unless method_defined?(:setbyte)
    def setbyte(i, b)
      self[i] = b
      b
    end
  end
  def ord; self[0]; end unless method_defined?(:ord)
  def force_encoding(*args); self; end unless method_defined?(:force_encoding)
  def b; self; end unless method_defined?(:b)
  def unpack1(fmt); unpack(fmt)[0]; end unless method_defined?(:unpack1)
end

class Array
  def sample; choice; end unless method_defined?(:sample)
end

class Symbol
  def to_proc
    sym = self
    Proc.new { |obj, *args| obj.__send__(sym, *args) }
  end unless method_defined?(:to_proc)
end
