/** @type {import('tailwindcss').Config} */

export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    container: {
      center: true,
    },
    extend: {
      fontFamily: {
        sans: ['"PingFang SC"', '"HarmonyOS Sans SC"', '"Inter"', 'system-ui', 'sans-serif'],
        display: ['"Manrope"', '"HarmonyOS Sans Bold"', '"Inter"', 'sans-serif'],
        mono: ['"JetBrains Mono"', '"Fira Code"', 'monospace'],
      },
      colors: {
        ink: {
          950: '#06060A',
          900: '#0B0B0F',
          850: '#13131A',
          800: '#1C1C24',
          700: '#2A2A33',
          600: '#3A3A45',
          500: '#54545F',
          400: '#7A7A85',
          300: '#A5A5B0',
        },
        ember: {
          50: '#FFF1E8',
          100: '#FFD9BF',
          200: '#FFB380',
          300: '#FF8F4D',
          400: '#FF7B33',
          500: '#FF6A2C',
          600: '#E14F14',
          700: '#B33D0D',
          800: '#7F2A09',
        },
        cyber: {
          50: '#E6FBF7',
          100: '#BFF4E8',
          200: '#7FE9D6',
          300: '#3CE0C6',
          400: '#1FCBB0',
          500: '#0FA890',
        },
        signal: {
          green: '#3CE0C6',
          orange: '#FF6A2C',
          violet: '#A78BFA',
          rose: '#FB7185',
          gold: '#FACC15',
        },
      },
      boxShadow: {
        glow: '0 0 0 1px rgba(255,106,44,0.35), 0 8px 30px -8px rgba(255,106,44,0.45)',
        cyber: '0 0 0 1px rgba(60,224,198,0.35), 0 8px 30px -8px rgba(60,224,198,0.45)',
        panel: '0 30px 80px -20px rgba(0,0,0,0.65), 0 0 0 1px rgba(255,255,255,0.04)',
      },
      backgroundImage: {
        'grid-faint':
          'linear-gradient(rgba(255,255,255,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.04) 1px, transparent 1px)',
        'noise':
          "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 0.06 0'/></filter><rect width='100%' height='100%' filter='url(%23n)'/></svg>\")",
      },
      backgroundSize: {
        'grid-32': '32px 32px',
      },
      animation: {
        'pulse-ring': 'pulseRing 2.4s ease-out infinite',
        'ping-slow': 'pingSlow 3s ease-in-out infinite',
        'shimmer': 'shimmer 2.6s linear infinite',
        'float': 'float 6s ease-in-out infinite',
      },
      keyframes: {
        pulseRing: {
          '0%':   { transform: 'scale(0.85)', opacity: '0.9' },
          '80%':  { transform: 'scale(1.4)',  opacity: '0'   },
          '100%': { transform: 'scale(1.4)',  opacity: '0'   },
        },
        pingSlow: {
          '0%, 100%': { transform: 'scale(1)',   opacity: '0.6' },
          '50%':      { transform: 'scale(1.15)', opacity: '1'   },
        },
        shimmer: {
          '0%':   { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%':      { transform: 'translateY(-6px)' },
        },
      },
    },
  },
  plugins: [],
}
