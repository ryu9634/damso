/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        damso: {
          50: '#fff8f1',
          100: '#feecd6',
          200: '#fcd5a8',
          300: '#fab574',
          400: '#f78f3e',
          500: '#ef6f1c',
          600: '#d65514',
          700: '#a73f12',
          800: '#7a2f10',
          900: '#52210c',
        },
      },
    },
  },
  plugins: [],
};
