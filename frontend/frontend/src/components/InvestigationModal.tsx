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
    };
    onClose: () => void;
    onComplete: () => void;
}

interface Evidence {
    submitId: number;  // 고유한 submitId로 key 사용
    evidenceDescription: string;  // 증거 설명
    isTrueEvidence: boolean;
}

interface Suspect {
    suspectId: number;  // 고유한 suspectId 추가
    suspectName: string;
    description: string;
}

export function InvestigationModal({ caseData, onClose, onComplete }: InvestigationModalProps) {
    const [evidence, setEvidence] = useState<Evidence[]>([]);
    const [culpritName, setCulpritName] = useState('');
    const [suspects, setSuspects] = useState<Suspect[]>([]);
    const [selectedSuspect, setSelectedSuspect] = useState('');
    const [reasoning, setReasoning] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        fetchCaseDetails();
    }, [caseData.caseId]);

    const fetchCaseDetails = async () => {
        try {
            console.log("Fetching case details for caseId:", caseData.caseId);

            // 실제 API를 사용하여 범인 이름과 증거를 받아옵니다.
            const response = await apiClient.get(`/cases/${caseData.caseId}/details`);
            console.log("API response:", response.data);

            const { culpritName, evidence } = response.data;
            setCulpritName(culpritName); // 범인 이름 설정
            setEvidence(evidence); // 증거 목록 설정

            // 용의자 목록 설정: 범인과 임의 용의자들
            setSuspects([
                { suspectId: 1, suspectName: culpritName, description: '피해자의 동료, 최근 다툼이 있었음' },  // 범인
                { suspectId: 2, suspectName: '용의자 A', description: '피해자의 동료, 최근 다툼이 있었음' },
                { suspectId: 3, suspectName: '용의자 B', description: '피해자의 친구, 금전 거래가 있었음' },
                { suspectId: 4, suspectName: '용의자 C', description: '피해자의 이웃, 소음 문제로 불편함을 표시' }
            ]);
        } catch (error) {
            console.log("Error fetching case details:", error);
            toast.error("사건 세부 정보를 불러오는 데 실패했습니다.");
        }
    };

    const handleSubmit = async () => {
        if (!selectedSuspect) return;
        setIsSubmitting(true);

        console.log("Submitting guess:", selectedSuspect, "with reasoning:", reasoning);

        try {
            // 추리 제출 API 호출
            const response = await apiClient.patch(`/cases/${caseData.caseId}/submit-guess`, {
                culpritGuess: selectedSuspect,
                reasoning,
                status: '추리 완료'
            });
            console.log("Guess submitted successfully:", response.data);

            toast.success(`'${selectedSuspect}'를 범인으로 추리 제출했습니다.`);
            onComplete();
        } catch (error) {
            console.log("Error submitting guess:", error);
            toast.error("추리 제출에 실패했습니다.");
        } finally {
            setIsSubmitting(false);
        }
    };

    const getDifficultyStars = (difficulty: number) => {
        return '⭐'.repeat(difficulty);
    };

    // 렌더링 중 데이터 확인용 로그 추가
    console.log("Evidence List:", evidence); // 이 부분을 추가하여 데이터가 있는지 확인
    console.log("Suspects List:", suspects);

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
                                    key={item.submitId}  // `submitId`로 고유하게 설정
                                    className="p-4 hover:shadow-md transition-shadow"
                                >
                                    <div className="flex items-start gap-3">
                                        <CheckCircle className="size-5 text-blue-500 mt-0.5 flex-shrink-0" />
                                        <p className="text-sm flex-1">{item.evidenceDescription}</p>  {/* `evidenceDescription` 필드로 수정 */}
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
                            {suspects.map((suspect) => (
                                <Card
                                    key={suspect.suspectId}  // 고유한 key로 suspectId 사용
                                    className={`p-4 cursor-pointer transition-all hover:shadow-md ${selectedSuspect === suspect.suspectName ? 'ring-2 ring-purple-500 bg-purple-50' : 'hover:bg-gray-50'}`}
                                    onClick={() => setSelectedSuspect(suspect.suspectName)}
                                >
                                    <div className="flex items-start gap-3">
                                        <Search className="size-5 text-purple-500 mt-0.5 flex-shrink-0" />
                                        <div className="flex-1">
                                            <h4 className="mb-1">{suspect.suspectName}</h4>
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
