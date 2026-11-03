# essentia_translate.rb — Essentia (GPLv2, like the rest of Essentia)
#
# Automatic translation of games that aren't in Spanish (optional, per game).
# The game's texts go through Pokémon Essentials' message system; here they
# are looked up in a translations file kept by Essentia. Texts not translated
# yet are asked for (Essentia translates them in the background: official
# Pokémon names from a glossary, the rest with the translation service).
# Dialogue waits a moment for its translation; menus show the original until
# theirs arrives. Works with both Ruby engines (no newer Ruby syntax here).

module EssentiaTranslate
  FILE = ENV['ESSENTIA_TR_FILE'].to_s
  REQ = ENV['ESSENTIA_TR_REQ'].to_s
  @map = {}
  @done = {}
  @pending = {}
  @pos = 0
  @frame = 0
  @hooked = {}

  class << self
    def utf8(s)
      s.respond_to?(:force_encoding) ? s.force_encoding('UTF-8') : s
    end

    def unesc(s)
      utf8(s.gsub(/\\(.)/) do
        case $1
        when 'n' then "\n"
        when 't' then "\t"
        when 'r' then "\r"
        else $1
        end
      end)
    end

    def esc(s)
      s.gsub('\\') { '\\\\' }.gsub("\n", '\\n').gsub("\t", '\\t').gsub("\r", '\\r')
    end

    # New lines of the translations file (Essentia appends to it)
    def load_new
      return if FILE.empty? || !File.exist?(FILE)
      size = File.size(FILE)
      @pos = 0 if size < @pos
      return if size == @pos
      data = File.open(FILE, 'rb') { |f| f.seek(@pos); f.read }
      last = data.rindex("\n")
      return unless last
      @pos += last + 1
      data[0, last].split("\n").each do |line|
        a, b = line.split("\t", 2)
        next if b.nil? || a.nil? || a.empty?
        o = unesc(a)
        t = unesc(b)
        @map[o] = t
        @done[t] = true
      end
    rescue Exception
    end

    def request(text)
      return if @pending[text] || REQ.empty?
      @pending[text] = true
      File.open(REQ, 'ab') { |f| f.write(esc(text) + "\n") }
    rescue Exception
    end

    # The translation of a text, or the text itself (asking for it).
    # wait: seconds to wait for a translation that isn't there yet.
    def lookup(text, wait = 0)
      return text unless text.is_a?(String)
      return text if text.empty? || @done[text]
      return text unless text =~ /[A-Za-z]{2}/
      t = @map[text]
      return t if t
      request(text)
      if wait > 0
        deadline = Time.now + wait
        while Time.now < deadline
          sleep(0.05)
          load_new
          t = @map[text]
          return t if t
        end
      end
      text
    rescue Exception
      text
    end

    # Kinds of names that stay as they are (Pokémon, trainers)
    def keep_type?(type)
      return false unless defined?(MessageTypes)
      @keep ||= begin
        list = []
        %w[Species SPECIES_NAMES TrainerNames TRAINER_NAMES SpeciesForms FORM_NAMES].each do |c|
          list << MessageTypes.const_get(c) if MessageTypes.const_defined?(c)
        end
        list
      end
      @keep.include?(type)
    rescue Exception
      false
    end

    # ------------------------------------------------------------ hooks

    def wrap_top(name, wait, kind)
      targets = []
      targets << Object if Object.private_method_defined?(name) || Object.method_defined?(name)
      meta = class << Kernel; self; end
      targets << meta if meta.method_defined?(name)
      targets.each do |t|
        current = t.instance_method(name)
        next if @hooked[[t, name]] == current
        raw = "essentia_tr_raw_#{name}".to_sym
        t.send(:alias_method, raw, name)
        if kind == :message
          t.send(:define_method, name) do |*args, &block|
            args[1] = EssentiaTranslate.lookup(args[1], wait) if args.length > 1
            __send__(raw, *args, &block)
          end
        else # :commands
          t.send(:define_method, name) do |*args, &block|
            if args.length > 1 && args[1].is_a?(Array)
              args[1] = args[1].map { |c| EssentiaTranslate.lookup(c, wait) }
            end
            __send__(raw, *args, &block)
          end
        end
        t.send(:private, name) if t == Object
        @hooked[[t, name]] = t.instance_method(name)
      end
    rescue Exception
    end

    def wrap_messages
      return unless defined?(MessageTypes)
      meta = class << MessageTypes; self; end
      %w[getFromHash getFromMapHash get].each do |m|
        name = m.to_sym
        next unless meta.method_defined?(name)
        current = meta.instance_method(name)
        next if @hooked[[meta, name]] == current
        raw = "essentia_tr_raw_#{m}".to_sym
        meta.send(:alias_method, raw, name)
        if m == 'getFromMapHash'
          meta.send(:define_method, name) do |*args|
            EssentiaTranslate.lookup(__send__(raw, *args), 0)
          end
        else
          meta.send(:define_method, name) do |*args|
            r = __send__(raw, *args)
            EssentiaTranslate.keep_type?(args[0]) ? r : EssentiaTranslate.lookup(r, 0)
          end
        end
        @hooked[[meta, name]] = meta.instance_method(name)
      end
    rescue Exception
    end

    def install
      wrap_top(:pbMessageDisplay, 2.5, :message)
      wrap_top(:pbShowCommands, 1.5, :commands)
      wrap_messages
    end

    def tick
      @frame += 1
      install if @frame == 1 || (@frame % 120).zero?
      load_new if (@frame % 15).zero?
    end
  end
end

EssentiaTranslate.load_new

module Graphics
  class << self
    alias_method :essentia_tr_original_update, :update
    def update(*args)
      essentia_tr_original_update(*args)
      EssentiaTranslate.tick
    end
  end
end
