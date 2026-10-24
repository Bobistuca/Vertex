# Third-party notices

Vertex is distributed under the GNU General Public License, version 3 or (at your option) any later version
(see `LICENSE`). `Vertex.jar` embeds the following libraries, relocated into the
`io.github.bobistuca.vertex.libs` namespace so they cannot clash with other plugins:

| Library | Version | License | Purpose |
|---|---|---|---|
| [PacketEvents](https://github.com/retrooper/packetevents) (`packetevents-spigot`, `packetevents-api`, `packetevents-netty-common`) | 2.14.0 | GPL-3.0 | Server-side packet observation |
| [Adventure](https://github.com/KyoriPowered/adventure) (`adventure-api`, `adventure-key`, `adventure-text-minimessage`, serializers pulled in by PacketEvents) and `examination-*` | 4.26.1 / 1.3.0 | MIT | MiniMessage template parsing and the text model used by PacketEvents |
| [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) | 2.5 | Apache-2.0 | Safe parsing of `config.yml` and locale files |

Because PacketEvents is licensed under the GPL-3.0, the combined `Vertex.jar` is conveyed under the GPL-3.0 as
well. The corresponding source code is this repository.

Compile-time only (not bundled):

| Library | License | Purpose |
|---|---|---|
| Spigot API (`spigot-api`) | GPL-3.0 with classpath exception | Bukkit/Spigot API baseline |
| Paper API (`paper-api`) | GPL-3.0 with classpath exception | Paper-only adapter |
| LuckPerms API (`net.luckperms:api`) | MIT | Optional group auditing, loaded only when LuckPerms is present |

Test only: JUnit 5 (EPL-2.0).

Minecraft is a trademark of Mojang AB. Vertex is not affiliated with Mojang, Microsoft, PaperMC, SpigotMC or
the PacketEvents project.
