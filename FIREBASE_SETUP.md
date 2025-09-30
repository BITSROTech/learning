# Firebase Firestore 설정 가이드

## 1. Firebase Console 설정

### Firestore Database 생성
1. [Firebase Console](https://console.firebase.google.com)에서 프로젝트 선택
2. 왼쪽 메뉴에서 **Firestore Database** 클릭
3. **데이터베이스 만들기** 클릭
4. **프로덕션 모드**로 시작 선택
5. 위치 선택 (asia-northeast3 - 서울 추천)

### Security Rules 설정
1. Firestore Database > 규칙 탭 클릭
2. 아래 규칙을 복사하여 붙여넣기:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // 사용자는 자신의 프로필만 읽고 쓸 수 있음
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && 
        (request.auth.uid == userId || 
         request.auth.uid == resource.data.uid ||
         request.auth.uid == userId.split(':')[1]); // Kakao 사용자 처리
    }
    
    // 학교 통계는 모든 로그인 사용자가 읽을 수 있고, 시스템만 쓸 수 있음
    match /schoolStats/{school} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // 학년 통계는 모든 로그인 사용자가 읽을 수 있고, 시스템만 쓸 수 있음
    match /gradeStats/{grade} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // 문제 풀이 기록은 본인것만 쓸 수 있고, 모두 읽을 수 있음
    match /solveRecords/{record} {
      allow read: if request.auth != null;
      allow create: if request.auth != null && 
        request.resource.data.userId == request.auth.uid;
      allow update, delete: if request.auth != null && 
        resource.data.userId == request.auth.uid;
    }
  }
}
```

3. **게시** 버튼 클릭

## 2. 인덱스 생성 (성능 최적화)

Firestore Database > 색인 탭에서 다음 복합 색인 추가:

### users 컬렉션
- 필드: `totalScore` (내림차순), `__name__` (오름차순)
- 쿼리 범위: 컬렉션

### schoolStats 컬렉션
- 필드: `totalScore` (내림차순), `__name__` (오름차순)
- 쿼리 범위: 컬렉션

## 3. 문제 해결

### 점수가 Firebase에 저장되지 않는 경우

1. **로그 확인**
   - Android Studio Logcat에서 "SolveViewModel" 태그로 필터링
   - Firebase 저장 성공/실패 메시지 확인

2. **인증 상태 확인**
   - Google 로그인 사용자: Firebase Auth에 자동 연동
   - Kakao 로그인 사용자: "kakao:유저ID" 형식으로 저장

3. **권한 문제**
   - Security Rules가 올바르게 설정되었는지 확인
   - Firebase Console > Firestore > 규칙 탭에서 확인

4. **네트워크 문제**
   - 인터넷 연결 상태 확인
   - Firebase 프로젝트가 활성화되어 있는지 확인

### 리더보드에 데이터가 표시되지 않는 경우

1. **데이터 확인**
   - Firebase Console > Firestore Database > 데이터 탭
   - `users` 컬렉션에 사용자 데이터 확인
   - `totalScore` 필드가 0이 아닌지 확인

2. **실시간 업데이트**
   - 점수 획득 후 약간의 시간이 필요할 수 있음
   - 리더보드 화면을 새로고침 (화면 나갔다가 다시 진입)

## 4. 데이터 구조

```
Firestore Database
├── users/                     # 사용자 프로필
│   └── {userId}/
│       ├── uid: string
│       ├── name: string
│       ├── email: string
│       ├── school: string
│       ├── grade: number
│       ├── totalScore: number
│       ├── solvedProblems: number
│       └── ...
│
├── schoolStats/              # 학교별 통계
│   └── {schoolName}/
│       ├── schoolName: string
│       ├── totalStudents: number
│       ├── totalScore: number
│       └── ...
│
├── gradeStats/               # 학년별 통계
│   └── grade_{1-12}/
│       ├── grade: number
│       ├── totalStudents: number
│       ├── totalScore: number
│       └── ...
│
└── solveRecords/            # 문제 풀이 기록
    └── {auto-id}/
        ├── userId: string
        ├── subject: string
        ├── isCorrect: boolean
        ├── points: number
        └── ...
```

## 5. 테스트 방법

1. 앱 실행 및 로그인
2. 프로필 설정 (학교, 학년 입력)
3. 문제 풀이 (70점 이상 받기)
4. 설정 화면에서 점수 확인
5. Firebase Console에서 데이터 확인
6. 리더보드에서 순위 확인