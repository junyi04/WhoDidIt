import { useState, useEffect } from 'react';
import { Card } from './ui/card';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { X, Search, CheckCircle } from 'lucide-react';
import { toast } from 'sonner';
import axios from 'axios';

const apiClient = axios.create({ baseURL: '/api', withCredentials: true });

interface InvestigationModalProps {
    caseData: {
        activeId: number;
        caseId: number;
        caseTitle: string;
        caseDescription: string;
        difficulty: number;
        // ⭐
        suspects: string[]; 
    };
    // ⭐ detectiveId props
    detectiveId: number;
    onClose: () => void;
    onComplete: () => void;
}

interface Evidence {
    submitId: number;
    evidenceDescription: string;
    isTrueEvidence: boolean;
}

// ⭐ [수정됨] 백엔드에서 이름 목록만 제공하므로, 인터페이스를 이름(name) 기반으로 단순화합니다.
interface Suspect {
    name: string; // 용의자 닉네임 (case_suspect 테이블의 suspect_name)
    description?: string; // (선택사항) 설명은 임의로 표시하거나 제거할 수 있습니다.
}

export function InvestigationModal({ caseData, onClose, onComplete }: InvestigationModalProps) {
    const [evidence, setEvidence] = useState<Evidence[]>([]);
    // const [culpritName, setCulpritName] = useState(''); // 이제 사용하지 않음
    const [suspects, setSuspects] = useState<Suspect[]>([]); // 용의자 목록
    const [selectedSuspect, setSelectedSuspect] = useState('');
    const [reasoning, setReasoning] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        fetchCaseDetails();
    }, [caseData.caseId, caseData.suspects]); // caseData.suspects가 변경될 때도 fetch를 재실행하도록 의존성 배열에 추가

    const fetchCaseDetails = async () => {
        try {
            console.log("Fetching case details for caseId:", caseData.caseId);

            // 1. 증거 및 범인 정보만 가져오는 API 사용
            const response = await apiClient.get(`/cases/${caseData.caseId}/details`);
            console.log("API response:", response.data);

            const { culpritName, evidence } = response.data;
            setEvidence(evidence); // 증거 목록 설정

            // ⭐ 2. 용의자 이름 배열 
            const initialSuspects: Suspect[] = caseData.suspects.map((name) => ({
                name: name,
            }));

            // ⭐ 진짜 범인 닉네임 추가
            const trueCulpritSuspect: Suspect = {
                name: culpritName,
            };

            const combinedSuspects = [...initialSuspects, trueCulpritSuspect];

            // ⭐ 3. 하드코딩된 목록 대신 백엔드에서 받은 데이터를 상태에 설정합니다.
            setSuspects(combinedSuspects); 

        } catch (error) {
            console.error("Error fetching case details:", error);
            toast.error("사건 세부 정보를 불러오는 데 실패했습니다.");
        }
    };

    const handleSubmit = async () => {
        if (!selectedSuspect) return;
        setIsSubmitting(true);

        console.log("Submitting guess:", selectedSuspect, "with reasoning:", reasoning);

        try {
            // 1. API 호출
            const response = await apiClient.patch(`/cases/${caseData.caseId}/submit-guess`, {
                culpritGuessNickname: selectedSuspect,
                reasoning,
                status: '추리 완료'
            });
            
            console.log("Guess submitted successfully:", response.data);
            toast.success(`'${selectedSuspect}'를 범인으로 추리 제출했습니다.`);

            // ⭐ 1. 모달 닫기 전에 제출 상태를 먼저 해제합니다.
            setIsSubmitting(false); 

            // ⭐ 2. 상태 해제 후, 모달을 닫도록 요청합니다.
            onComplete(); 
            
        } catch (error) {
            console.error("Error submitting guess:", error);
            toast.error("추리 제출에 실패했습니다.");
            
            // ⭐ 3. 실패 시에도 상태를 해제해야 합니다.
            setIsSubmitting(false); 
            
        } 
    };

    const getDifficultyStars = (difficulty: number) => {
        return '⭐'.repeat(difficulty);
    };

    // 렌더링 중 데이터 확인용 로그 추가
    console.log("Evidence List:", evidence);
    console.log("Suspects List (Transformed):", suspects);

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
            <Card className="w-full max-w-4xl max-h-[90vh] overflow-y-auto">
                <div className="sticky top-0 bg-white border-b p-6 z-10">
                    <div className="flex justify-between items-start">
                        <div className="flex-1">
                            <div className="flex items-center gap-3 mb-2">
                                <h2>사건 조사</h2>
                                <span className="text-yellow-500">{getDifficultyStars(caseData.difficulty)}</span>
                            </div>
                            <h3 className="mb-1">{caseData.caseTitle}</h3>
                            <p className="text-sm text-muted-foreground">{caseData.caseDescription}</p>
                        </div>
                        <Button onClick={onClose} variant="ghost" size="sm">
                            <X className="size-4" />
                        </Button>
                    </div>
                </div>

                <div className="p-6 space-y-6">
                    {/* 증거 목록 */}
                    <div>
                        <div className="flex items-center gap-2 mb-4">
                            <h3>증거 자료</h3>
                            <Badge variant="secondary">총 {evidence.length}개</Badge>
                        </div>
                        <div className="space-y-2">
                            {evidence.map((item) => (
                                <Card 
                                    key={item.submitId}
                                    className="p-4 hover:shadow-md transition-shadow"
                                >
                                    <div className="flex items-start gap-3">
                                        <CheckCircle className="size-5 text-blue-500 mt-0.5 flex-shrink-0" />
                                        <p className="text-sm flex-1">{item.evidenceDescription}</p>
                                    </div>
                                </Card>
                            ))}
                        </div>
                    </div>

                    {/* 용의자 목록 */}
                    <div>
                        <div className="mb-4">
                            <h3 className="mb-2">용의자 목록</h3>
                            <p className="text-sm text-muted-foreground">증거를 바탕으로 범인을 선택하세요</p>
                        </div>
                        <div className="space-y-2">
                            {/* ⭐ 4. suspects 상태의 데이터를 렌더링합니다. */}
                            {suspects.map((suspect, index) => (
                                <Card
                                    key={index} // suspectId가 없으므로 index를 사용
                                    className={`p-4 cursor-pointer transition-all hover:shadow-md ${selectedSuspect === suspect.name ? 'ring-2 ring-purple-500 bg-purple-50' : 'hover:bg-gray-50'}`}
                                    onClick={() => setSelectedSuspect(suspect.name)} // ⭐ suspect.name 사용
                                >
                                    <div className="flex items-start gap-3">
                                        <Search className="size-5 text-purple-500 mt-0.5 flex-shrink-0" />
                                        <div className="flex-1">
                                            <h4 className="mb-1">{suspect.name}</h4> {/* ⭐ suspect.name 사용 */}
                                            {/* description 필드가 있다면 여기에 표시 */}
                                            <p className="text-sm text-muted-foreground">{suspect.description}</p>
                                        </div>
                                    </div>
                                </Card>
                            ))}
                        </div>
                    </div>

                    {/* 추리 근거 */}
                    {selectedSuspect && (
                        <div>
                            <h3 className="mb-2">추리 근거 (선택사항)</h3>
                            <textarea
                                value={reasoning}
                                onChange={(e) => setReasoning(e.target.value)}
                                placeholder="범인을 선택한 이유를 설명하세요..."
                                className="w-full min-h-[100px] p-3 border rounded-lg resize-none focus:outline-none focus:ring-2 focus:ring-purple-500"
                            />
                        </div>
                    )}
                </div>

                <div className="sticky bottom-0 bg-white border-t p-6 flex justify-end gap-3 z-10">
                    <Button onClick={onClose} variant="outline" disabled={isSubmitting}>
                        나중에
                    </Button>
                    <Button
                        onClick={handleSubmit}
                        disabled={!selectedSuspect || isSubmitting}
                        className="bg-purple-500 hover:bg-purple-600"
                    >
                        {isSubmitting ? (
                            <>
                                <Search className="size-4 mr-2 animate-spin" />
                                제출 중...
                            </>
                        ) : (
                            <>
                                <Search className="size-4 mr-2" />
                                추리 제출
                            </>
                        )}
                    </Button>
                </div>
            </Card>
        </div>
    );
}