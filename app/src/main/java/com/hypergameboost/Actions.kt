        }
        Snaps.map[name] = m
        "Snapshot $name saved (${m.size} entries)"
    }

    fun diffSnaps(): String {
        val a = Snaps.map["A"]
        val b = Snaps.map["B"]
        if (a == null || b == null) return "Pehle A aur B dono snapshot lein."
        val out = (a.keys + b.keys).toSortedSet().mapNotNull { k ->
            val x = a[k]
            val y = b[k]
            if (x != y) "$k: ${x ?: "(none)"} -> ${y ?: "(none)"}" else null
        }
        return if (out.isEmpty()) "Koi fark nahi mila." else "${out.size} badlav:\n" + out.take(80).joinToString("\n")
    }

    /** Overlay permission Shizuku se (settings mein jaane ki zarurat nahi). */
    suspend fun grantOverlay(pkg: String): String = withContext(Dispatchers.IO) {
        require(pkgRegex.matches(pkg))
        Sh.run("appops set $pkg SYSTEM_ALERT_WINDOW allow").ifBlank { "Overlay permission de di gayi." }
    }

    // ---- v0.6: DNS benchmark, background data firewall, Bluetooth volume ----

    suspend fun dnsBenchmark(): String = withContext(Dispatchers.IO) {
        listOf("1.1.1.1" to "Cloudflare", "8.8.8.8" to "Google", "9.9.9.9" to "Quad9").joinToString("\n") { (ip, name) ->
            val r = Sh.run("ping -c 5 -i 0.3 -W 2 $ip 2>&1 | tail -1")
            "$name ($ip): " + r.ifBlank { "koi jawab nahi" }
        }
    }

    @Volatile
    private var firewallOn = false

    /**
     * Social apps ka background data Data Saver ke zariye band. Dhyan: Data Saver poore phone par on hota hai,
