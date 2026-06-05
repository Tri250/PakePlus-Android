import { gradeColors } from '../data/mockData';
import type { Grade } from '../data/mockData';

interface Props {
  name: string;
  color: string;
  size?: number;
  grade?: Grade;
  showGrade?: boolean;
}

export default function Avatar({ name, color, size = 40, grade, showGrade = false }: Props) {
  return (
    <div className="relative flex-shrink-0">
      <div
        className="rounded-full flex items-center justify-center text-white font-semibold"
        style={{
          width: size,
          height: size,
          background: color,
          fontSize: size * 0.4,
          boxShadow: '0 2px 6px rgba(0,0,0,0.10)',
        }}
      >
        {name}
      </div>
      {showGrade && grade && (
        <div
          className="absolute -bottom-0.5 -right-0.5 rounded-full flex items-center justify-center font-bold"
          style={{
            width: size * 0.42,
            height: size * 0.42,
            background: gradeColors[grade].bg,
            color: gradeColors[grade].text,
            fontSize: size * 0.22,
            border: '2px solid #fff',
          }}
        >
          {grade}
        </div>
      )}
    </div>
  );
}
