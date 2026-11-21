# Probe: Remote Control Cell Builder

Development probe. Writes `~/Library/Logs/Bitwig/remote-widget-probe.log`.

Hooks the two candidate cell builders in Bitwig 6.1.7:

- `com.bitwig.flt.widget.core.device.ila.Gef(LWrh;)LdwF;`
- `com.bitwig.flt.widget.core.device.lbk.Gef(LWrh;)LdwF;`

Each call logs the builder name, the entry's class, and (once per class) the
entry's methods, so the next step can read a parameter's step count and swap the
knob for a toggle or a dropdown.
